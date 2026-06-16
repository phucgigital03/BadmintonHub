package com.badmintonhub.payment.messaging;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.ProcessedEvent;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.messaging.event.PaymentOrphanedEvent;
import com.badmintonhub.payment.messaging.event.RefundRequiredEvent;
import com.badmintonhub.payment.repository.PaymentRepository;
import com.badmintonhub.payment.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional handling of booking-service compensations. The payment mutation + the idempotency-guard row
 * commit together (one {@code @Transactional}); the listener acks only after this returns, so a crash before
 * the ack just replays — and the {@code processed_events} check makes the replay a no-op. A bad payload
 * throws → the listener's error handler retries → DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventHandler {

    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * booking.payment.orphaned → a CONFIRMED payment lost its booking (cancelled before STAFF confirmed).
     * Flag it for a manual refund so STAFF sees it instead of money silently sitting on a dead order. The
     * payment stays CONFIRMED (the cash did arrive) — refund is executed manually via {@code /refund}.
     */
    @Transactional
    public void handleOrphaned(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentOrphanedEvent event = parse(payload, PaymentOrphanedEvent.class);
        Payment p = paymentRepository.findByIdForUpdate(event.paymentId()).orElse(null);
        if (p == null) {
            log.warn("booking.payment.orphaned for unknown payment {} — skipping", event.paymentId());
            recordProcessed(eventId);
            return;
        }
        if (p.getStatus() == PaymentStatus.CONFIRMED && !p.isRefundRequired()) {
            p.setRefundRequired(true);
            p.setRefundRequiredReason(event.reason()); // BOOKING_CANCELLED
            paymentRepository.save(p);
            log.warn("Payment {} flagged refund-required — booking {} was cancelled (reason={})",
                    p.getId(), event.bookingId(), event.reason());
        } else {
            log.debug("Payment {} not CONFIRMED-and-unflagged ({}, refundRequired={}) on orphan — no-op",
                    p.getId(), p.getStatus(), p.isRefundRequired());
        }
        recordProcessed(eventId);
    }

    /**
     * booking.refund.required → a paid (CONFIRMED) booking was cancelled within the refund window. Flag the
     * booking's active payment for a manual refund and store the policy-computed suggested amount, so it
     * surfaces in the STAFF refund queue ({@code /refund-required}). Without this the refund tier computed
     * by booking-service would be a silent dead end and the user would never get their money back. The
     * payment stays CONFIRMED — STAFF executes the bank transfer via {@code /refund}.
     */
    @Transactional
    public void handleRefundRequired(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        RefundRequiredEvent event = parse(payload, RefundRequiredEvent.class);
        // Prefer the CONFIRMED payment (the one actually holding the money) over a stray PROOF_SUBMITTED,
        // then row-lock it so we don't race a concurrent STAFF confirm/refund on the same payment.
        Payment p = paymentRepository
                .findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(event.bookingId(), List.of(PaymentStatus.CONFIRMED))
                .or(() -> paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(
                        event.bookingId(), List.of(PaymentStatus.PROOF_SUBMITTED)))
                .flatMap(found -> paymentRepository.findByIdForUpdate(found.getId()))
                .orElse(null);
        if (p == null) {
            // No live payment to refund (never paid, or already REFUNDED/EXPIRED) — nothing to flag.
            log.warn("booking.refund.required for booking {} but no CONFIRMED/PROOF payment — skipping",
                    event.bookingId());
            recordProcessed(eventId);
            return;
        }
        if (!p.isRefundRequired()) {
            p.setRefundRequired(true);
            p.setRefundRequiredReason(event.reason()); // BOOKING_CANCELLED_BY_USER
            p.setRefundRequiredAmount(event.refundAmount());
            paymentRepository.save(p);
            log.warn("Payment {} flagged refund-required — paid booking {} cancelled (reason={}, amount={})",
                    p.getId(), event.bookingId(), event.reason(), event.refundAmount());
        } else {
            log.debug("Payment {} already refund-required — no-op", p.getId());
        }
        recordProcessed(eventId);
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) {
            // Outbox always sets msgKey = event UUID, so a null key means a misconfigured producer —
            // idempotency can't dedupe this message. Warn loudly rather than silently risk reprocessing.
            log.warn("Kafka event arrived with a NULL key — idempotency guard disabled for this message");
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Event {} already processed — skipping", eventId);
            return true;
        }
        return false;
    }

    private void recordProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId));
        }
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            // Unparseable payload → throw so the error handler retries then routes to the DLT.
            throw new IllegalStateException("Cannot parse " + type.getSimpleName() + " payload: " + e.getMessage(), e);
        }
    }
}
