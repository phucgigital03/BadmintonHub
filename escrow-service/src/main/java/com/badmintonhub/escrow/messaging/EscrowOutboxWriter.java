package com.badmintonhub.escrow.messaging;

import com.badmintonhub.escrow.entity.OutboxEvent;
import com.badmintonhub.escrow.entity.enums.OutboxStatus;
import com.badmintonhub.escrow.messaging.event.HostReimbursedEvent;
import com.badmintonhub.escrow.messaging.event.RefundQueuedEvent;
import com.badmintonhub.escrow.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Persists Outbox rows. Call from within an escrow {@code @Transactional} so the event commits
 * atomically with the escrow change (the Outbox guarantee). The publisher does the actual Kafka send.
 */
@Component
@RequiredArgsConstructor
public class EscrowOutboxWriter {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void writeHostReimbursed(UUID matchId, UUID hostId, BigDecimal amount) {
        String eventId = UUID.randomUUID().toString();
        persist(EscrowTopics.HOST_REIMBURSED, eventId, new HostReimbursedEvent(eventId, matchId, hostId, amount));
    }

    public void writeRefundQueued(UUID matchId, BigDecimal amount) {
        String eventId = UUID.randomUUID().toString();
        persist(EscrowTopics.REFUND_QUEUED, eventId, new RefundQueuedEvent(eventId, matchId, amount));
    }

    private void persist(String topic, String eventId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setMsgKey(eventId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            // Payloads are plain records — this should never happen; fail the escrow tx if it does.
            throw new IllegalStateException("Cannot serialize outbox payload for topic " + topic, e);
        }
        event.setStatus(OutboxStatus.PENDING);
        outboxRepository.save(event);
    }
}
