package ru4dh4n.ordermatching.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;


@Service
public class JwtValidationService {
    private final SecretKey secretKey;
    private final Cache<String, Boolean> usedTokenCache;

    public JwtValidationService(@Value("${jwt.secret}") String secret,
                                @Value("${jwt.replay.cache.expiry.minutes:15}") long cacheExpiryMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        this.usedTokenCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cacheExpiryMinutes))
                .build();
    }

    public Optional<String> extractUserId(String jwt) {
        Optional<String> userId = Optional.empty();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();

            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) { throw new JwtException("Expired token"); }

            String jti = claims.getId();
            if (jti == null) { throw new JwtException("Missing jti"); }

            if (usedTokenCache.getIfPresent(jti) != null) { throw new JwtException("Token already used"); }

            usedTokenCache.put(jti, true);

            userId = Optional.ofNullable(claims.getSubject());
        } catch (JwtException e) {
            // TODO: log this
        } catch (Exception ignored) { }
        return userId;
    }
}
