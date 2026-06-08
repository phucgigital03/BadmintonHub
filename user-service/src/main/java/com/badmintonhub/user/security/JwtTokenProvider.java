package com.badmintonhub.user.security;

import com.badmintonhub.security.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Issues HS256 access tokens that satisfy the shared {@link JwtUtil} claim contract
 * (sub = userId, claim {@code roles} = List&lt;String&gt;, jti = UUID). {@code JwtUtil}
 * only validates — this is the producer side.
 *
 * <p>Refresh tokens are opaque (not JWTs): the raw value is sent in the cookie, its BCrypt
 * hash is stored on the user. The raw value carries the userId so the service can look the
 * user up on refresh: {@code userId + ":" + UUID}.</p>
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(UTF_8));
        this.accessExpirationMs = accessExpirationMs;
    }

    /** Holder returned by {@link #generateAccessToken} so the caller can blacklist by jti and report expiry. */
    public record AccessToken(String token, String jti, Instant expiresAt) {}

    public AccessToken generateAccessToken(UUID userId, Collection<String> roles) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(accessExpirationMs);

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtUtil.CLAIM_ROLES, List.copyOf(roles))
                .id(jti)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new AccessToken(token, jti, expiresAt);
    }

    /**
     * Generate the raw (opaque) refresh-token value for a user.
     * Format {@code userId + ":" + UUID} so the user can be looked up on refresh
     * (the stored value is the BCrypt hash of this string).
     */
    public String generateRefreshTokenValue(UUID userId) {
        return userId + ":" + UUID.randomUUID();
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }
}
