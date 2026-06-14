package com.badmintonhub.booking.messaging;

import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.BookingItem;
import com.badmintonhub.booking.entity.ProcessedEvent;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.messaging.event.PaymentConfirmedEvent;
import com.badmintonhub.booking.messaging.event.PaymentExpiredEvent;
import com.badmintonhub.booking.messaging.event.PaymentProofSubmittedEvent;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import com.badmintonhub.booking.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transactional handling of payment outcomes for court bookings: the booking mutation + the
 * idempotency-guard row commit together (one {@code @Transactional}). The listener acks only after this
 * returns, so a crash before the ack just replays — and the {@code processed_events} check makes the
 * replay a no-op. A bad payload throws → the listener's error handler retries → DLT.
 *
 * <p>Events whose {@code bookingId} is null (e.g. a MATCH_PLAYER payment also published on
 * {@code payment.player.*}) are not ours — recorded as processed and skipped.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final ObjectMapper objectMapper;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outboxWriter;

    /**
     * Proof submitted → pause the booking hold ({@code hold_expires_at = null}) so HoldExpiryScheduler
     * stops auto-cancelling. The booking stays PENDING; STAFF confirm/reject (the payment.player.*
     * events) decides the final outcome. Symmetric with payment-service, which also waits at PROOF_SUBMITTED.
     */
    @Transactional
    public void handleProofSubmitted(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentProofSubmittedEvent event = parse(payload, PaymentProofSubmittedEvent.class);
        if (event.bookingId() == null) {
            recordProcessed(eventId); // proof for a MATCH_* payment — not ours
            return;
        }
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("payment.proof.submitted for unknown booking {} — skipping", event.bookingId());
            recordProcessed(eventId);
            return;
        }
        if (booking.getStatus() == BookingStatus.PENDING && booking.getHoldExpiresAt() != null) {
            booking.setHoldExpiresAt(null); // proof received → stop the auto-cancel clock; STAFF decides
            bookingRepository.save(booking);
            log.info("Booking {} hold paused — proof submitted (payment {})", booking.getId(), event.paymentId());
        } else {
            log.debug("Booking {} not PENDING-with-hold ({}) on payment.proof.submitted — no-op",
                    booking.getId(), booking.getStatus());
        }
        recordProcessed(eventId);
    }

    /** Payment confirmed → booking PENDING becomes CONFIRMED; held slots stay RESERVED (never released). */
    @Transactional
    public void handleConfirmed(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentConfirmedEvent event = parse(payload, PaymentConfirmedEvent.class);
        if (event.bookingId() == null) {
            recordProcessed(eventId); // not a court-booking payment — nothing to do
            return;
        }
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("payment.confirmed for unknown booking {} — skipping", event.bookingId());
            recordProcessed(eventId);
            return;
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setHoldExpiresAt(null); // a paid booking must not be picked up by HoldExpiryScheduler
            bookingRepository.save(booking);
            log.info("Booking {} → CONFIRMED (payment {})", booking.getId(), event.paymentId());
        } else if (booking.getStatus() == BookingStatus.CANCELLED) {
            // Money was confirmed for a booking that is already cancelled (hold timed out / user cancelled
            // before STAFF confirmed). We must NOT resurrect it — instead emit a compensating event so
            // payment-service flags the payment for a manual refund (zombie-event pattern, Never-Violate #6).
            outboxWriter.writePaymentOrphaned(event.paymentId(), booking.getId());
            log.warn("Booking {} already CANCELLED but payment {} CONFIRMED → emitting booking.payment.orphaned",
                    booking.getId(), event.paymentId());
        } else {
            log.debug("Booking {} not PENDING ({}) on payment.confirmed — no-op",
                    booking.getId(), booking.getStatus());
        }
        recordProcessed(eventId);
    }

    /** Payment expired/rejected → booking PENDING becomes CANCELLED + its slots are released. */
    @Transactional
    public void handleExpired(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentExpiredEvent event = parse(payload, PaymentExpiredEvent.class);
        if (event.bookingId() == null) {
            recordProcessed(eventId);
            return;
        }
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("payment.expired for unknown booking {} — skipping", event.bookingId());
            recordProcessed(eventId);
            return;
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            // Capture slot ids before deleting the items — needed for the release event.
            List<UUID> slotIds = bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(booking.getId())
                    .stream().map(BookingItem::getSlotId).toList();

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason("PAYMENT_EXPIRED");
            booking.setCancelledAt(LocalDateTime.now());
            booking.setRefundAmount(BigDecimal.ZERO); // unpaid hold — nothing to refund
            bookingRepository.save(booking);

            bookingItemRepository.deleteByBooking_Id(booking.getId());
            outboxWriter.writeSlotReleased(booking.getId(), slotIds); // same tx → court flips slots AVAILABLE
            log.info("Booking {} → CANCELLED + {} slot(s) released (payment {} expired)",
                    booking.getId(), slotIds.size(), event.paymentId());
        } else {
            log.debug("Booking {} not PENDING ({}) on payment.expired — no-op",
                    booking.getId(), booking.getStatus());
        }
        recordProcessed(eventId);
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId != null && processedEventRepository.existsById(eventId)) {
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
