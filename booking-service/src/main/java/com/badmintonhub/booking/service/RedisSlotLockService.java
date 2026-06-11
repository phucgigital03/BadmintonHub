package com.badmintonhub.booking.service;

import com.badmintonhub.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Per-slot distributed locks for the booking-creation critical section. Acquires {@code lock:slot:{slotId}}
 * for every selected slot all-or-nothing; if any is already held, releases what it took and throws 409.
 *
 * <p><b>Fail-open by design</b>: if Redis itself is unavailable the create proceeds <i>unlocked</i> — the
 * {@code booking_items.slot_id} UNIQUE constraint is the authoritative double-book backstop, so correctness
 * is preserved even without the lock (the lock only avoids contention and a confusing retry).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSlotLockService {

    private static final String KEY_PREFIX = "lock:slot:";
    private static final String LOCK_VALUE = "1";
    /** Short TTL: the lock only needs to cover the create transaction (redis-patterns.md). */
    public static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;

    /**
     * Acquire a lock for every slot. Returns the keys actually held (to release in a {@code finally});
     * an empty list means Redis was unavailable and the caller is proceeding on the DB UNIQUE backstop.
     *
     * @throws ConflictException if any slot is currently locked by another in-flight booking
     */
    public List<String> acquireAll(Collection<java.util.UUID> slotIds) {
        List<String> acquired = new ArrayList<>();
        for (java.util.UUID slotId : slotIds) {
            String key = KEY_PREFIX + slotId;
            Boolean ok;
            try {
                ok = redis.opsForValue().setIfAbsent(key, LOCK_VALUE, LOCK_TTL);
            } catch (Exception e) {
                // Redis down → release what we hold and proceed unlocked (UNIQUE(slot_id) still guards).
                log.warn("Redis unavailable acquiring slot locks; proceeding on DB UNIQUE backstop: {}",
                        e.getMessage());
                releaseAll(acquired);
                return List.of();
            }
            if (!Boolean.TRUE.equals(ok)) {
                releaseAll(acquired);
                throw new ConflictException("SLOT_LOCKED",
                        "Một hoặc nhiều ô đang được người khác đặt, vui lòng thử lại");
            }
            acquired.add(key);
        }
        return acquired;
    }

    public void releaseAll(Collection<String> keys) {
        for (String key : keys) {
            try {
                redis.delete(key);
            } catch (Exception e) {
                log.warn("Failed releasing slot lock {} (will expire in {}s): {}",
                        key, LOCK_TTL.toSeconds(), e.getMessage());
            }
        }
    }
}
