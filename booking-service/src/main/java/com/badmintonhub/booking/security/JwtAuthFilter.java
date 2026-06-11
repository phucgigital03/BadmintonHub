package com.badmintonhub.booking.security;

import com.badmintonhub.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-service defense-in-depth JWT filter. Re-validates the forwarded
 * {@code Authorization: Bearer} token with the shared {@link JwtUtil} and derives identity
 * (userId + roles) from the verified claims. The token is the single source of identity —
 * this filter NEVER trusts {@code X-User-Id} / {@code X-User-Roles} headers.
 *
 * <p>When the token's {@code email_verified} claim is true, a synthetic {@code EMAIL_VERIFIED}
 * authority is added so controllers can enforce Never-Violate #10 via
 * {@code @PreAuthorize("hasRole('USER') and hasAuthority('EMAIL_VERIFIED')")} — no call to user-service.</p>
 *
 * <p>If no/invalid token is present it simply continues the chain without setting authentication;
 * the {@code SecurityFilterChain} then rejects protected routes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** Synthetic authority marking a verified email — enforced by @PreAuthorize for booking creation. */
    public static final String EMAIL_VERIFIED_AUTHORITY = "EMAIL_VERIFIED";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseAndValidate(authHeader.substring(BEARER_PREFIX.length()));
            String userId = jwtUtil.getUserId(claims);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>(jwtUtil.getRoles(claims).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList());
            if (jwtUtil.isEmailVerified(claims)) {
                authorities.add(new SimpleGrantedAuthority(EMAIL_VERIFIED_AUTHORITY));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid/expired token → leave the request anonymous; the chain rejects protected routes.
            log.debug("JWT validation failed, continuing unauthenticated: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
