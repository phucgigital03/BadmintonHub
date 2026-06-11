package com.badmintonhub.court.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes the booking-slot Saga events and flips court-service slots accordingly. Thin by design: the
 * transactional work lives in {@link BookingSlotEventHandler}; the ack happens only after it returns
 * cleanly (manual ack). The event UUID arrives as the Kafka message key and drives idempotency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final BookingSlotEventHandler handler;

    @KafkaListener(topics = "booking.slot.held", groupId = "court-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onSlotHeld(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleHeld(record.key(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.slot.released", groupId = "court-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onSlotReleased(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handleReleased(record.key(), record.value());
        ack.acknowledge();
    }
}
