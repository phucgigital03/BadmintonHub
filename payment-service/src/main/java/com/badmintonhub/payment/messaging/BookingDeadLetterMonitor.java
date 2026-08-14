package com.badmintonhub.payment.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

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

    static final String PAYMENT_ORPHANED_DLT = "booking.payment.orphaned.DLT";
    static final String REFUND_REQUIRED_DLT = "booking.refund.required.DLT";

    /** Every topic known at startup — each gets its counter pre-registered at 0 (see the constructor). */
    static final List<String> DEAD_LETTER_TOPICS = List.of(PAYMENT_ORPHANED_DLT, REFUND_REQUIRED_DLT);

    private static final String METRIC = "payment.booking.deadletter.total";
    private static final String DESCRIPTION =
            "booking.payment.orphaned / booking.refund.required events dead-lettered after retries — a "
                    + "payment that should be flagged refund_required never was (won't appear in "
                    + "/refund-required; user owed money; needs manual replay)";

    private final MeterRegistry meterRegistry;

    public BookingDeadLetterMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Pre-register every known topic so its series exists at 0 BEFORE the first dead letter.
        // The alert is increase(payment_booking_deadletter_total[5m]) > 0: registering lazily inside
        // record() makes the series appear already at 1, leaving no step for increase() to see — so the
        // FIRST dead letter is missed, which is exactly when one refund is silently owed.
        DEAD_LETTER_TOPICS.forEach(this::counterFor);
    }

    @KafkaListener(topics = {PAYMENT_ORPHANED_DLT, REFUND_REQUIRED_DLT},
            groupId = "payment-service", containerFactory = "manualAckListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        record(record.topic(), record.key(), record.value());
        ack.acknowledge();
    }

    /** Count + log a dead-lettered compensation event. Package-private so it's unit-testable without a broker. */
    void record(String topic, String key, String payload) {
        counterFor(topic == null ? "unknown" : topic).increment();
        log.error("[DLT] booking compensation event dead-lettered after retries on {} — payment NOT flagged "
                + "for refund; user may be owed money with no STAFF signal. key={} payload={}",
                topic, key, payload);
    }

    /**
     * Idempotent: Micrometer returns the already-registered counter for a name+tag it knows, so calling
     * this from both the constructor (pre-register) and {@link #record} (increment, plus the lazy
     * "unknown" fallback for a null topic) keeps a single counter per topic.
     */
    private Counter counterFor(String topic) {
        return Counter.builder(METRIC)
                .description(DESCRIPTION)
                .tag("topic", topic)
                .register(meterRegistry);
    }
}
