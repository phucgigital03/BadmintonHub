package com.badmintonhub.escrow.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes the payment/match events that drive the escrow ledger. Thin by design: the transactional
 * work lives in {@link EscrowEventHandler}; the ack happens only after it returns cleanly (manual ack).
 * The event UUID arrives as the Kafka message key and drives idempotency. A thrown handler exception is
 * retried (2/4/8s) then routed to {@code {topic}.DLT} by the container error handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowEventListener {

    private final EscrowEventHandler handler;

    @KafkaListener(topics = EscrowTopics.PAYMENT_HOST_CONFIRMED, groupId = "escrow-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onHostPaymentConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleHostPaymentConfirmed(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = EscrowTopics.PAYMENT_PLAYER_CONFIRMED, groupId = "escrow-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onPlayerPaymentConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handlePlayerPaymentConfirmed(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = EscrowTopics.MATCH_COMPLETED, groupId = "escrow-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onMatchCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleMatchCompleted(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = EscrowTopics.MATCH_CANCELLED, groupId = "escrow-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onMatchCancelled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleMatchCancelled(record.key(), record.value());
        ack.acknowledge();
    }
}
