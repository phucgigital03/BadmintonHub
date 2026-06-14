package com.badmintonhub.payment.messaging;

import com.badmintonhub.payment.entity.OutboxEvent;
import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.OutboxStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;
import com.badmintonhub.payment.messaging.event.PaymentConfirmedEvent;
import com.badmintonhub.payment.messaging.event.PaymentExpiredEvent;
import com.badmintonhub.payment.messaging.event.ProofSubmittedEvent;
import com.badmintonhub.payment.messaging.event.RefundProcessedEvent;
import com.badmintonhub.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Persists Outbox rows. Call from within a payment {@code @Transactional} so the event commits
 * atomically with the {@code payments.status} change (the Outbox guarantee). {@code OutboxPublisherScheduler}
 * does the actual Kafka send.
 *
 * <p>Confirm/expire pick the topic by {@link PaymentType}: {@code MATCH_HOST} → host topic, everything
 * else → player topic (booking + escrow consume the player topic; the carried ids let each consumer skip
 * what isn't theirs).</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentOutboxWriter {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void writeProofSubmitted(Payment p) {
        String eventId = UUID.randomUUID().toString();
        persist(PaymentTopics.PROOF_SUBMITTED, eventId, new ProofSubmittedEvent(
                eventId, p.getId(), p.getBookingId(), p.getMatchId(), p.getUserId(), p.getOrderCode()));
    }

    public void writeConfirmed(Payment p) {
        String eventId = UUID.randomUUID().toString();
        String topic = p.getPaymentType() == PaymentType.MATCH_HOST
                ? PaymentTopics.HOST_CONFIRMED : PaymentTopics.PLAYER_CONFIRMED;
        persist(topic, eventId, new PaymentConfirmedEvent(eventId, p.getId(), p.getBookingId(),
                p.getMatchId(), p.getEnrollmentId(), p.getUserId(), p.getAmount(), p.getPaymentType()));
    }

    public void writeExpired(Payment p) {
        String eventId = UUID.randomUUID().toString();
        String topic = p.getPaymentType() == PaymentType.MATCH_HOST
                ? PaymentTopics.HOST_EXPIRED : PaymentTopics.PLAYER_EXPIRED;
        persist(topic, eventId, new PaymentExpiredEvent(eventId, p.getId(), p.getBookingId(),
                p.getMatchId(), p.getUserId(), p.getPaymentType()));
    }

    public void writeRefundProcessed(Payment p) {
        String eventId = UUID.randomUUID().toString();
        persist(PaymentTopics.REFUND_PROCESSED, eventId,
                new RefundProcessedEvent(eventId, p.getId(), p.getUserId(), p.getRefundAmount()));
    }

    private void persist(String topic, String eventId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setMsgKey(eventId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            // Payloads are plain records — this should never happen; fail the payment tx if it does.
            throw new IllegalStateException("Cannot serialize outbox payload for topic " + topic, e);
        }
        event.setStatus(OutboxStatus.PENDING);
        outboxRepository.save(event);
    }
}
