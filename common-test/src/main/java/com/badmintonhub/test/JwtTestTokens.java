package com.badmintonhub.test;

import com.badmintonhub.security.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints HS256 JWTs for tests so secured endpoints can be exercised. Tokens follow the same claim
 * contract the gateway and services read via {@link JwtUtil}: {@code sub}=userId, {@code roles}
 * (= {@link JwtUtil#CLAIM_ROLES}), {@code jti}.
 *
 * <p>Use the same secret your test profile sets for {@code jwt.secret}.</p>
 *
 * <pre>{@code
 * String auth = JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER");
 * mockMvc.perform(post("/api/bookings").header("Authorization", auth) ...);
 * }</pre>
 */
public final class JwtTestTokens {

    private JwtTestTokens() {
    }

    /** Raw signed token (15-minute expiry). */
    public static String token(String secret, String userId, String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim(JwtUtil.CLAIM_ROLES, List.of(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }

    /** Token prefixed with {@code "Bearer "} — ready for the Authorization header. */
    public static String bearer(String secret, String userId, String... roles) {
        return "Bearer " + token(secret, userId, roles);
    }
}
