package com.badmintonhub.payment.scheduler;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.messaging.PaymentOutboxWriter;
import com.badmintonhub.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Expires unpaid payments whose countdown has elapsed and emits a {@code payment.*.expired} Outbox event
 * (booking releases its slots; matchmaking cancels the match). Only {@code PENDING} is expired —
 * {@code PROOF_SUBMITTED} is awaiting STAFF review and must not be auto-cancelled (the payer already
 * transferred). The {@code expires_at} column is the authoritative deadline (not the Redis countdown).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxWriter outboxWriter;

    @Scheduled(fixedDelay = 60_000) // every 60s
    @Transactional
    public void expireStalePayments() {
        List<Payment> stale = paymentRepository
                .findByStatusAndExpiresAtBefore(PaymentStatus.PENDING, LocalDateTime.now());
        if (stale.isEmpty()) {
            return;
        }
        for (Payment p : stale) {
            p.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(p);
            outboxWriter.writeExpired(p);
            log.info("Payment {} (#{}) expired (deadline {}) → EXPIRED + {} event",
                    p.getId(), p.getOrderCode(), p.getExpiresAt(), p.getPaymentType());
        }
        log.info("PaymentExpiryScheduler expired {} stale payment(s)", stale.size());
    }
}
