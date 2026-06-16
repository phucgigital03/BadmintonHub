package com.badmintonhub.booking.service;

import com.badmintonhub.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-user throttle on booking creation. A single user must not be able to squat a club's grid by
 * spamming {@code POST /api/bookings} — each create holds the selected slots for the (15-min) hold
 * window, so unthrottled creates are a denial-of-revenue vector.
 *
 * <p>Fixed-window counter in Redis: {@code rate_limit:booking:{userId}}, INCR + EXPIRE on first hit,
 * reject once the count exceeds {@link #MAX_PER_WINDOW} within {@link #WINDOW}. The cap is generous
 * for real users and fatal to scripts; the gateway's global {@code RequestRateLimiter} is a second
 * layer.</p>
 *
 * <p><b>Fail-open by design</b> (matching {@link RedisSlotLockService}): if Redis is unavailable the
 * create proceeds rather than locking out legitimate users during a Redis outage.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRateLimiter {

    private static final String KEY_PREFIX = "rate_limit:booking:";
    private static final int MAX_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    /**
     * @throws TooManyRequestsException if the caller has exceeded {@link #MAX_PER_WINDOW} creates in the
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
            // Redis down → don't block legitimate bookings; the gateway limiter still applies.
            log.warn("Redis unavailable for booking rate-limit (user {}); failing open: {}",
                    userId, e.getMessage());
            return;
        }
        if (count != null && count > MAX_PER_WINDOW) {
            throw new TooManyRequestsException("RATE_LIMITED",
                    "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút");
        }
    }
}
