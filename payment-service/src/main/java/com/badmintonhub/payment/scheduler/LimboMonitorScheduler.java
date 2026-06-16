package com.badmintonhub.payment.scheduler;

import com.badmintonhub.payment.entity.enums.OutboxStatus;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.repository.OutboxEventRepository;
import com.badmintonhub.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability for money sitting in limbo. Two signals, surfaced as {@code log.warn} + Micrometer gauges:
 * <ul>
 *   <li>{@code payment.outbox.stuck} — Outbox rows PENDING long past the ~3s publish cycle (Kafka down →
 *       confirm/expire/refund events not delivered).</li>
 *   <li>{@code payment.proof.stuck} — payments PROOF_SUBMITTED past the review SLA: the payer already
 *       transferred but STAFF hasn't confirmed/rejected, so the money is unreconciled.</li>
 * </ul>
 * Neither auto-acts (STAFF reviews manually) — the point is to alert instead of failing silently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimboMonitorScheduler {

    private static final long STUCK_OUTBOX_MINUTES = 2;
    /** Proof awaiting STAFF longer than this is flagged — the payer's money is unreviewed. */
    private static final long PROOF_REVIEW_SLA_MINUTES = 30;

    private final OutboxEventRepository outboxRepository;
    private final PaymentRepository paymentRepository;
    private final MeterRegistry meterRegistry;
    private final AtomicLong stuckOutbox = new AtomicLong(0);
    private final AtomicLong stuckProof = new AtomicLong(0);

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("payment.outbox.stuck", stuckOutbox);
        meterRegistry.gauge("payment.proof.stuck", stuckProof);
    }

    @Scheduled(fixedDelay = 120_000) // every 2 min
    public void monitor() {
        LocalDateTime now = LocalDateTime.now();

        long outboxStuck = outboxRepository.countByStatusAndCreatedAtBefore(
                OutboxStatus.PENDING, now.minusMinutes(STUCK_OUTBOX_MINUTES));
        stuckOutbox.set(outboxStuck);
        if (outboxStuck > 0) {
            log.warn("[LIMBO] {} payment outbox event(s) still PENDING >{}min — Kafka publish stuck; "
                    + "confirm/expire/refund events undelivered until this drains",
                    outboxStuck, STUCK_OUTBOX_MINUTES);
        }

        long proofStuck = paymentRepository.countByStatusAndUpdatedAtBefore(
                PaymentStatus.PROOF_SUBMITTED, now.minusMinutes(PROOF_REVIEW_SLA_MINUTES));
        stuckProof.set(proofStuck);
        if (proofStuck > 0) {
            log.warn("[LIMBO] {} payment(s) PROOF_SUBMITTED >{}min awaiting STAFF review — money "
                    + "transferred but unreconciled; check /api/payments/pending-review",
                    proofStuck, PROOF_REVIEW_SLA_MINUTES);
        }
    }
}
