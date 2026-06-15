package com.badmintonhub.court.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes the booking-slot Saga and flips court-service slots accordingly. Thin by design: the
 * transactional work lives in {@link BookingSlotEventHandler}; the ack happens only after it returns
 * cleanly (manual ack). One topic ({@code booking.slot.changed}) keyed by slotId → a slot's HELD/RELEASED
 * arrive in order on one partition. Idempotency uses the eventId inside the payload (the key is the slotId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final BookingSlotEventHandler handler;

    @KafkaListener(topics = "booking.slot.changed", groupId = "court-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onSlotChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handler.handle(record.value());
        ack.acknowledge();
    }
}
