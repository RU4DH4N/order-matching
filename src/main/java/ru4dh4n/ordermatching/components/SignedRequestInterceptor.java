package ru4dh4n.ordermatching.components;


import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;
import ru4dh4n.ordermatching.services.JwtValidationService;

import java.util.Optional;

@Component
@GrpcGlobalServerInterceptor
public class SignedRequestInterceptor implements ServerInterceptor {
    public static final Context.Key<String> AUTH_USER_ID = Context.key("auth-user-id");
    public static final Metadata.Key<String> AUTH_HEADER_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer "; // TODO: this annoys me

    private final JwtValidationService jwtValidationService;
    private final PublicMethodRegistry publicMethodRegistry;

    public SignedRequestInterceptor(JwtValidationService jwtValidationService, PublicMethodRegistry publicMethodRegistry) {
        this.jwtValidationService = jwtValidationService;
        this.publicMethodRegistry = publicMethodRegistry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();

        if (methodName.contains("grpc.reflection") || publicMethodRegistry.isPublicMethod(methodName)) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTH_HEADER_KEY);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Authorization header is missing or invalid"), new Metadata());
            return new ServerCall.Listener<>() { };
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            Optional<String> userIdOpt = jwtValidationService.extractUserId(jwt);

            if (userIdOpt.isEmpty()) {
                call.close(Status.UNAUTHENTICATED.withDescription("JWT is invalid"), new Metadata());
                return new ServerCall.Listener<>() { };
            }

            Context ctx = Context.current().withValue(AUTH_USER_ID, userIdOpt.get());
            return Contexts.interceptCall(ctx, call, headers, next);
        } catch (Exception e) {
            call.close(Status.UNAUTHENTICATED.withDescription("JWT validation failed"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }
}