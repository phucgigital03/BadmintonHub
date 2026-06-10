package com.badmintonhub.gateway.filter;

import com.badmintonhub.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single auth gate for the whole platform. Runs at highest precedence — before the route-level
 * {@code RequestRateLimiter} and before {@code NettyRoutingFilter} — so it can reject bad tokens
 * before any routing happens.
 *
 * <p>On success it stashes the userId in an exchange attribute ({@link #USER_ID_ATTR}) for the
 * rate-limit {@code KeyResolver}, and forwards the original {@code Authorization: Bearer} header
 * untouched so each downstream service can re-validate the token (defense in depth). It does NOT
 * emit {@code X-User-Id}/{@code X-User-Roles} — the verified token is the single source of identity.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** Exchange attribute key holding the authenticated userId (gateway-internal, never forwarded). */
    public static final String USER_ID_ATTR = "gateway.userId";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/**", "/actuator/**");
    /** Anonymous read-only browse: club/court/pricing/slot-grid GETs need no token. */
    private static final List<String> PUBLIC_GET_PATHS = List.of("/api/clubs/**", "/api/courts/**");

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        // CORS preflight (OPTIONS) carries no Authorization header — let it through so the gateway's
        // globalcors can answer it; otherwise protected-path preflight would 401 and CORS would fail.
        // Public paths + anonymous read-only browse (GET /api/clubs|courts/**) also skip the token gate.
        if (HttpMethod.OPTIONS.equals(method) || isPublic(path) || isPublicGet(method, path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeError(exchange, "TOKEN_MISSING", "Thiếu hoặc sai định dạng token");
        }

        final Claims claims;
        try {
            claims = jwtUtil.parseAndValidate(authHeader.substring(BEARER_PREFIX.length()));
        } catch (ExpiredJwtException e) {
            return writeError(exchange, "TOKEN_EXPIRED", "Token đã hết hạn");
        } catch (JwtException | IllegalArgumentException e) {
            return writeError(exchange, "TOKEN_INVALID", "Token không hợp lệ");
        }

        String userId = jwtUtil.getUserId(claims);
        String jti = jwtUtil.getJti(claims);

        return isBlacklisted(jti).flatMap(revoked -> {
            if (Boolean.TRUE.equals(revoked)) {
                return writeError(exchange, "TOKEN_REVOKED", "Token đã bị thu hồi");
            }
            if (userId != null) {
                exchange.getAttributes().put(USER_ID_ATTR, userId);
            }
            // Authorization header is left untouched so downstream services re-validate the same token.
            return chain.filter(exchange);
        });
    }

    /** Blacklist lookup with fail-open semantics: if Redis is unreachable, allow (best-effort). */
    private Mono<Boolean> isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey("session:blacklist:" + jti)
                .onErrorResume(ex -> {
                    log.warn("Redis blacklist check failed, failing open for jti={}: {}", jti, ex.toString());
                    return Mono.just(false);
                });
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /** Read-only browse is anonymous; mutations (POST/PATCH/...) still require a valid token. */
    private boolean isPublicGet(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method)
                && PUBLIC_GET_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
