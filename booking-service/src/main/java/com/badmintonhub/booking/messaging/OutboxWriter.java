package com.badmintonhub.booking.messaging;

import com.badmintonhub.booking.entity.OutboxEvent;
import com.badmintonhub.booking.entity.enums.OutboxStatus;
import com.badmintonhub.booking.messaging.event.PaymentOrphanedEvent;
import com.badmintonhub.booking.messaging.event.RefundRequiredEvent;
import com.badmintonhub.booking.messaging.event.SlotHeldEvent;
import com.badmintonhub.booking.messaging.event.SlotReleasedEvent;
import com.badmintonhub.booking.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persists Outbox rows. Call from within a booking {@code @Transactional} so the event commits
 * atomically with the booking change (the Outbox guarantee). The publisher does the actual Kafka send.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void writeSlotHeld(UUID bookingId, List<UUID> slotIds, LocalDateTime holdExpiresAt) {
        String eventId = UUID.randomUUID().toString();
        persist(BookingTopics.SLOT_HELD, eventId, new SlotHeldEvent(eventId, bookingId, slotIds, holdExpiresAt));
    }

    public void writeSlotReleased(UUID bookingId, List<UUID> slotIds) {
        String eventId = UUID.randomUUID().toString();
        persist(BookingTopics.SLOT_RELEASED, eventId, new SlotReleasedEvent(eventId, bookingId, slotIds));
    }

    public void writePaymentOrphaned(UUID paymentId, UUID bookingId) {
        String eventId = UUID.randomUUID().toString();
        persist(BookingTopics.PAYMENT_ORPHANED, eventId,
                new PaymentOrphanedEvent(eventId, paymentId, bookingId, "BOOKING_CANCELLED"));
    }

    /** A paid booking was cancelled in the refund window → tell payment to flag a refund of {@code amount}. */
    public void writeRefundRequired(UUID bookingId, BigDecimal refundAmount, String reason) {
        String eventId = UUID.randomUUID().toString();
        persist(BookingTopics.REFUND_REQUIRED, eventId,
                new RefundRequiredEvent(eventId, bookingId, refundAmount, reason));
    }

    private void persist(String topic, String eventId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setMsgKey(eventId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            // Payloads are plain records — this should never happen; fail the booking tx if it does.
            throw new IllegalStateException("Cannot serialize outbox payload for topic " + topic, e);
        }
        event.setStatus(OutboxStatus.PENDING);
        outboxRepository.save(event);
    }
}
