package com.badmintonhub.payment.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes compensating events from booking-service. Thin by design: the transactional work lives in
 * {@link BookingEventHandler}; the ack happens only after it returns cleanly (manual ack). The event UUID
 * arrives as the Kafka message key and drives idempotency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final BookingEventHandler handler;

    @KafkaListener(topics = "booking.payment.orphaned", groupId = "payment-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onPaymentOrphaned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleOrphaned(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.refund.required", groupId = "payment-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onRefundRequired(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleRefundRequired(record.key(), record.value());
        ack.acknowledge();
    }
}
