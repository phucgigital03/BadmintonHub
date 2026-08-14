package com.badmintonhub.booking.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Surfaces dead-lettered payment events so a paid / owed booking is never silently lost. The
 * {@code payment.*} events booking-service consumes (proof.submitted / player.confirmed / player.expired)
 * are routed to {@code {topic}.DLT} after 3 failed retries (see {@code KafkaConsumerConfig}); without a
 * consumer that DLT is a silent drop. The worst case is real money loss:
 * {@code payment.player.confirmed.DLT} means a CONFIRMED payment never flipped its booking to CONFIRMED,
 * so {@code HoldExpiryScheduler} releases the slot — the user paid and lost the slot with no signal.
 *
 * <p>Does NOT auto-reprocess (a poison message would just re-fail — manual replay per Never-Violate #7).
 * It makes the failure loud + countable: an ERROR log carrying the topic + key + payload, plus a
 * Micrometer counter {@code booking.payment.deadletter.total} (tagged by topic) an alert can watch. It
 * mirrors court-service's {@code SlotDeadLetterMonitor} and pairs with the publish-side
 * {@code LimboMonitor} (Outbox stuck PENDING) to cover both halves of the gap: couldn't-publish vs
 * published-but-the-consumer-failed.</p>
 */
@Slf4j
@Component
public class PaymentDeadLetterMonitor {

    static final String PROOF_SUBMITTED_DLT = "payment.proof.submitted.DLT";
    static final String PLAYER_CONFIRMED_DLT = "payment.player.confirmed.DLT";
    static final String PLAYER_EXPIRED_DLT = "payment.player.expired.DLT";

    /** Every topic known at startup — each gets its counter pre-registered at 0 (see the constructor). */
    static final List<String> DEAD_LETTER_TOPICS =
            List.of(PROOF_SUBMITTED_DLT, PLAYER_CONFIRMED_DLT, PLAYER_EXPIRED_DLT);

    private static final String METRIC = "booking.payment.deadletter.total";
    private static final String DESCRIPTION =
            "payment.* events dead-lettered after retries — a booking state change that never applied "
                    + "(user may have paid / be owed a refund; needs manual replay)";

    private final MeterRegistry meterRegistry;

    public PaymentDeadLetterMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Pre-register every known topic so its series exists at 0 BEFORE the first dead letter.
        // The alert is increase(booking_payment_deadletter_total[5m]) > 0: registering lazily inside
        // record() makes the series appear already at 1, leaving no step for increase() to see — so the
        // FIRST dead letter is missed, which is exactly when one payment is silently stuck.
        DEAD_LETTER_TOPICS.forEach(this::counterFor);
    }

    @KafkaListener(topics = {PROOF_SUBMITTED_DLT, PLAYER_CONFIRMED_DLT, PLAYER_EXPIRED_DLT},
            groupId = "booking-service", containerFactory = "manualAckListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        record(record.topic(), record.key(), record.value());
        ack.acknowledge();
    }

    /** Count + log a dead-lettered payment event. Package-private so it's unit-testable without a broker. */
    void record(String topic, String key, String payload) {
        counterFor(topic == null ? "unknown" : topic).increment();
        log.error("[DLT] payment event dead-lettered after retries on {} — booking state NOT applied; "
                + "money may be at risk (paid-not-confirmed / refund-owed). key={} payload={}",
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
