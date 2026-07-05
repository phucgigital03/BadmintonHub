package com.badmintonhub.chat.service;

import com.badmintonhub.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §G.10 item 2 — {@code rate_limit:chat} enforce. Fixed-window counter in Redis: reject past the cap (429),
 * set the TTL on the first hit, and <b>fail open</b> if Redis is unreachable (a chat outage must not lock out
 * legitimate users — the gateway limiter is the second layer, §G.3).
 */
class ChatRateLimiterTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ChatRateLimiter limiter = new ChatRateLimiter(redis);

    private void stubIncrement(Long value) {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(value);
    }

    @Test
    void check_firstHit_setsExpiryAndPasses() {
        stubIncrement(1L);

        assertThatCode(() -> limiter.check(UUID.randomUUID())).doesNotThrowAnyException();
        verify(redis).expire(anyString(), any(Duration.class)); // TTL set only on the first hit of the window
    }

    @Test
    void check_overLimit_throwsTooManyRequests() {
        stubIncrement(31L); // MAX_PER_WINDOW = 30

        assertThatThrownBy(() -> limiter.check(UUID.randomUUID()))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting("code").isEqualTo("RATE_LIMITED");
    }

    @Test
    void check_redisUnavailable_failsOpen() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> limiter.check(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
