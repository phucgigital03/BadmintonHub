package com.badmintonhub.payment.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Surfaces dead-lettered booking compensation events so money owed back to a user is never silently
 * dropped. The events payment-service consumes ({@code booking.payment.orphaned}, {@code
 * booking.refund.required}) are routed to {@code {topic}.DLT} after 3 failed retries (see
 * {@code KafkaConsumerConfig}); without a consumer that DLT is a silent drop. The impact is real:
 * a dead-lettered compensation means the matching payment is NEVER flagged {@code refund_required}, so
 * it never appears in {@code GET /api/payments/refund-required} — STAFF never knows to transfer the
 * refund, and the user is owed money with no signal.
 *
 * <p>Does NOT auto-reprocess (a poison message would just re-fail — manual replay per Never-Violate #7).
 * It makes the failure loud + countable: an ERROR log carrying the topic + key + payload, plus a
 * Micrometer counter {@code payment.booking.deadletter.total} (tagged by topic) an alert can watch.
 * Mirrors court-service's {@code SlotDeadLetterMonitor} and pairs with the publish-side
 * {@code LimboMonitor}.</p>
 */
@Slf4j
@Component
public class BookingDeadLetterMonitor {

    private final MeterRegistry meterRegistry;

    public BookingDeadLetterMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = {"booking.payment.orphaned.DLT", "booking.refund.required.DLT"},
            groupId = "payment-service", containerFactory = "manualAckListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        record(record.topic(), record.key(), record.value());
        ack.acknowledge();
    }

    /** Count + log a dead-lettered compensation event. Package-private so it's unit-testable without a broker. */
    void record(String topic, String key, String payload) {
        Counter.builder("payment.booking.deadletter.total")
                .description("booking.payment.orphaned / booking.refund.required events dead-lettered after "
                        + "retries — a payment that should be flagged refund_required never was (won't appear "
                        + "in /refund-required; user owed money; needs manual replay)")
                .tag("topic", topic == null ? "unknown" : topic)
                .register(meterRegistry)
                .increment();
        log.error("[DLT] booking compensation event dead-lettered after retries on {} — payment NOT flagged "
                + "for refund; user may be owed money with no STAFF signal. key={} payload={}",
                topic, key, payload);
    }
}
