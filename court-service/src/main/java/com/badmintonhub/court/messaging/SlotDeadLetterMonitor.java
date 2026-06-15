package com.badmintonhub.court.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Surfaces dead-lettered slot events so a stuck slot is never silent. A {@code booking.slot.changed}
 * message that fails its 3 retries is routed to {@code booking.slot.changed.DLT}
 * (see {@code KafkaConsumerConfig}); without a consumer that DLT is a silent drop — meaning a HELD/RELEASED
 * never applied, so a slot may be stuck (RESERVED for a dead booking, or AVAILABLE that should be held).
 *
 * <p>This does NOT auto-reprocess (a poison message would just re-fail — manual replay per Never-Violate
 * #7). It makes the failure loud and countable: an ERROR log carrying the slotId (Kafka key) + payload so
 * ops can re-apply, plus a Micrometer counter {@code court.slot.deadletter.total} an alert can watch. It
 * pairs with booking-service's publish-side {@code LimboMonitor} (Outbox stuck PENDING) to cover both
 * halves of the gap: couldn't-publish vs published-but-the-consumer-failed.</p>
 */
@Slf4j
@Component
public class SlotDeadLetterMonitor {

    static final String DEAD_LETTER_TOPIC = "booking.slot.changed.DLT";

    private final Counter deadLetters;

    public SlotDeadLetterMonitor(MeterRegistry meterRegistry) {
        this.deadLetters = Counter.builder("court.slot.deadletter.total")
                .description("booking.slot.changed events dead-lettered after retries — a slot hold/release "
                        + "that never applied (slot may be stuck; needs manual replay)")
                .register(meterRegistry);
    }

    @KafkaListener(topics = DEAD_LETTER_TOPIC, groupId = "court-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        record(record.key(), record.value());
        ack.acknowledge();
    }

    /** Count + log a dead-lettered slot event. Package-private so it's unit-testable without a broker. */
    void record(String slotId, String payload) {
        deadLetters.increment();
        log.error("[DLT] slot event dead-lettered after retries — slot {} hold/release NOT applied; the slot "
                + "may be stuck and needs manual replay. payload={}", slotId, payload);
    }
}
