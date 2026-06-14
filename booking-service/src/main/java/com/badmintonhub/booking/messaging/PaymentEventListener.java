package com.badmintonhub.booking.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes payment outcomes and closes the booking-payment loop. Thin by design: the transactional work
 * lives in {@link PaymentEventHandler}; the ack happens only after it returns cleanly (manual ack). The
 * event UUID arrives as the Kafka message key and drives idempotency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventHandler handler;

    @KafkaListener(topics = "payment.proof.submitted", groupId = "booking-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onProofSubmitted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleProofSubmitted(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "payment.player.confirmed", groupId = "booking-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onPaymentConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleConfirmed(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "payment.player.expired", groupId = "booking-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onPaymentExpired(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleExpired(record.key(), record.value());
        ack.acknowledge();
    }
}
