package com.badmintonhub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Shared HS256 JWT verification utility.
 *
 * <p>Deliberately framework-free (no Spring, no web, no JPA) so the same class can be reused by the
 * reactive {@code api-gateway} and every MVC downstream service. The gateway verifies the token
 * before routing; each downstream service re-validates the forwarded {@code Authorization: Bearer}
 * token and derives identity from the claims — the token is the single source of identity.</p>
 *
 * <p><b>Claim contract</b> (token producers, e.g. user-service, MUST follow this):</p>
 * <ul>
 *   <li>{@code sub}  — userId (UUID string)</li>
 *   <li>{@code roles} — {@code List<String>}, e.g. {@code ["ROLE_USER"]}</li>
 *   <li>{@code jti}  — token id (UUID string), used for the Redis logout blacklist</li>
 *   <li>{@code email_verified} — {@code boolean}; lets any service enforce the email-verified guard
 *       (Never-Violate #10) straight from the token, with no call back to user-service</li>
 * </ul>
 */
public class JwtUtil {

    /** Claim name holding the user's roles — shared between token producers and consumers. */
    public static final String CLAIM_ROLES = "roles";

    /** Claim name holding the user's email-verified flag — shared between token producers and consumers. */
    public static final String CLAIM_EMAIL_VERIFIED = "email_verified";

    private final SecretKey key;

    /**
     * @param secret the HS256 signing secret (env {@code JWT_SECRET}); must be at least 32 bytes.
     */
    public JwtUtil(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verify the token signature and expiry, returning its claims.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException     if the token has expired
     * @throws io.jsonwebtoken.security.SignatureException if the signature does not match
     * @throws io.jsonwebtoken.JwtException             for any other malformed/invalid token
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    /** Whether the token's owner has a verified email. Defaults to {@code false} if the claim is absent. */
    public boolean isEmailVerified(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_EMAIL_VERIFIED, Boolean.class));
    }
}
