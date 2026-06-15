package com.badmintonhub.booking.scheduler;

import com.badmintonhub.booking.entity.enums.OutboxStatus;
import com.badmintonhub.booking.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability for silent-money / stuck-state risks. Counts Outbox rows that are still PENDING long after
 * they were written — the publisher normally drains them in seconds, so a non-zero count means Kafka is
 * unreachable and a {@code slot.released} / {@code refund.required} may not have reached its consumer
 * (slots stuck RESERVED, refunds not flagged). Surfaced as a {@code log.warn} + a Micrometer gauge
 * ({@code booking.outbox.stuck}) so an alert can fire instead of money/slots sitting in limbo unnoticed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimboMonitorScheduler {

    /** A healthy publish drains in ~3s; still PENDING after this means a real publish problem. */
    private static final long STUCK_OUTBOX_MINUTES = 2;

    private final OutboxEventRepository outboxRepository;
    private final MeterRegistry meterRegistry;
    private final AtomicLong stuckOutbox = new AtomicLong(0);

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("booking.outbox.stuck", stuckOutbox);
    }

    @Scheduled(fixedDelay = 120_000) // every 2 min
    public void monitor() {
        long stuck = outboxRepository.countByStatusAndCreatedAtBefore(
                OutboxStatus.PENDING, LocalDateTime.now().minusMinutes(STUCK_OUTBOX_MINUTES));
        stuckOutbox.set(stuck);
        if (stuck > 0) {
            log.warn("[LIMBO] {} booking outbox event(s) still PENDING >{}min — Kafka publish stuck; "
                    + "slots may stay RESERVED and refund flags undelivered until this drains",
                    stuck, STUCK_OUTBOX_MINUTES);
        }
    }
}
