package com.badmintonhub.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes the payment-screen countdown deadline to Redis ({@code payment:countdown:{paymentId}}).
 *
 * <p><b>Fail-open by design</b>: the countdown is a UX convenience only — the authoritative deadline is
 * {@code payments.expires_at} in the DB (the expiry scheduler reads that). If Redis is down the payment
 * still initiates; we just log and move on.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCountdownService {

    private static final String KEY_PREFIX = "payment:countdown:";

    private final StringRedisTemplate redis;

    public void start(UUID paymentId, LocalDateTime expiresAt, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + paymentId, expiresAt.toString(), ttl);
        } catch (Exception e) {
            log.warn("Redis unavailable setting payment countdown for {} (UX only, expires_at is authoritative): {}",
                    paymentId, e.getMessage());
        }
    }
}
