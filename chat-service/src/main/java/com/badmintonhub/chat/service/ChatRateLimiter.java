package com.badmintonhub.chat.service;

import com.badmintonhub.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-user throttle on message sends — stops a single user from flooding a thread. Mirrors
 * {@code booking-service} {@code BookingRateLimiter}: fixed-window counter in Redis
 * ({@code rate_limit:chat:{userId}}, INCR + EXPIRE on first hit), reject past {@link #MAX_PER_WINDOW}.
 *
 * <p><b>Fail-open by design:</b> if Redis is unavailable the send proceeds — a chat outage must not lock
 * out legitimate users; the gateway's global {@code RequestRateLimiter} is a second layer (§G.3).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRateLimiter {

    private static final String KEY_PREFIX = "rate_limit:chat:";
    private static final int MAX_PER_WINDOW = 30;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    /**
     * @throws TooManyRequestsException if the caller has exceeded {@link #MAX_PER_WINDOW} sends in the
     *                                  current window. No-op (fail-open) when Redis is unreachable.
     */
    public void check(UUID userId) {
        String key = KEY_PREFIX + userId;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for chat rate-limit (user {}); failing open: {}", userId, e.getMessage());
            return;
        }
        if (count != null && count > MAX_PER_WINDOW) {
            throw new TooManyRequestsException("RATE_LIMITED",
                    "Bạn gửi tin quá nhanh, vui lòng thử lại sau giây lát");
        }
    }
}
