package ru4dh4n.ordermatching.grpc.services;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru4dh4n.ordermatching.grpc.*;
import ru4dh4n.ordermatching.components.SignedRequestInterceptor;
import ru4dh4n.ordermatching.helper.Order;
import ru4dh4n.ordermatching.services.MatchingEngineService;
import ru4dh4n.ordermatching.services.TradePropagationService;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@GrpcService
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    private final MatchingEngineService matchingEngineService;
    private final TradePropagationService tradePropagationService;

    @Autowired
    public OrderServiceImpl(MatchingEngineService matchingEngineService, TradePropagationService tradePropagationService) {
        super();
        this.matchingEngineService = matchingEngineService;
        this.tradePropagationService = tradePropagationService;
    }

    @Override
    public void subscribeToOrderUpdates(OrderUpdateRequest request, StreamObserver<OrderUpdateStream> responseObserver) {
        Timestamp lastUpdate = request.getLastUpdate();
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(java.time.Instant.ofEpochSecond(lastUpdate.getSeconds(), lastUpdate.getNanos()));

        boolean result = tradePropagationService.subscribe(Long.parseLong(request.getOrderId()), timestamp, responseObserver);

        if (result) return;

        responseObserver.onError(Status.INTERNAL.withDescription("couldn't subscribe to updates for order: " + request.getOrderId()).asRuntimeException());
    }

    @Override
    public void placeOrder(OrderRequest request, StreamObserver<PlaceOrderResponse> responseObserver) {
        String authUserId = SignedRequestInterceptor.AUTH_USER_ID.get();
        if (authUserId == null) {
            responseObserver.onError(Status.INTERNAL.withDescription("Authentication context missing.").asRuntimeException());
            return;
        }

        try {

            BigDecimal price = new BigDecimal(request.getPrice());
            BigDecimal quantity = new BigDecimal(request.getQuantity());
            Order.Side side = toInternalSide(request.getSide());

            Optional<Long> orderId = this.matchingEngineService.submitOrder(authUserId, request.getInstrumentId(), side, price, quantity);

            if (orderId.isEmpty()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Orde r has been cancelled.").asRuntimeException());
                return;
            }

            PlaceOrderResponse response = PlaceOrderResponse.newBuilder().setOrderId(Objects.toString(orderId.get())).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NumberFormatException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Invalid number for for price or quantity").asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Argument(s) invalid").withCause(e).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to process order").withCause(e).asRuntimeException());
        }
    }

    // this is probably unnecessary, but I want it to be explicit
    private static Order.Side toInternalSide(OrderSide side) {
        return switch (side) {
            case BUY -> Order.Side.BUY;
            case SELL -> Order.Side.SELL;
            default -> throw new IllegalArgumentException("Unknown side " + side);
        };
    }
}
