package com.badmintonhub.payment.messaging;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.ProcessedEvent;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.messaging.event.PaymentOrphanedEvent;
import com.badmintonhub.payment.repository.PaymentRepository;
import com.badmintonhub.payment.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Event {} already processed — skipping", eventId);
            return;
        }
        PaymentOrphanedEvent event = parse(payload);
        Payment p = paymentRepository.findById(event.paymentId()).orElse(null);
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

    private void recordProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId));
        }
    }

    private PaymentOrphanedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentOrphanedEvent.class);
        } catch (Exception e) {
            // Unparseable payload → throw so the error handler retries then routes to the DLT.
            throw new IllegalStateException("Cannot parse PaymentOrphanedEvent payload: " + e.getMessage(), e);
        }
    }
}
