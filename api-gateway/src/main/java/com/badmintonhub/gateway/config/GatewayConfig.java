package com.badmintonhub.gateway.config;

import com.badmintonhub.gateway.filter.JwtAuthenticationFilter;
import com.badmintonhub.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class GatewayConfig {

    /**
     * Shared JWT verifier. Secret comes from {@code JWT_SECRET} (no default → fails fast at startup
     * if missing). Used by {@link JwtAuthenticationFilter} to verify the token before routing.
     */
    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret) {
        return new JwtUtil(secret);
    }

    /**
     * Token-bucket limiter backing the {@code RequestRateLimiter} default-filter.
     * {@code burstCapacity=100} with {@code replenishRate=2/s} approximates the "~100 requests/min"
     * intent (token-bucket is per-second, so this is not an exact fixed window). Tunable here.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(2, 100, 1);
    }

    /**
     * Rate-limit key resolver. Prefers the authenticated userId stashed by the JWT filter
     * ({@link JwtAuthenticationFilter#USER_ID_ATTR}); for public paths (no token) falls back to the
     * client IP so the key is never empty (an empty key makes {@code RedisRateLimiter} deny by default).
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            Object userId = exchange.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
            if (userId != null) {
                return Mono.just(userId.toString());
            }
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown");
            return Mono.just("ip:" + ip);
        };
    }
}
