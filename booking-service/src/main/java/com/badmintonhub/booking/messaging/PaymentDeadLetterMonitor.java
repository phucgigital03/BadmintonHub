package com.badmintonhub.booking.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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

    private final MeterRegistry meterRegistry;

    public PaymentDeadLetterMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = {"payment.proof.submitted.DLT", "payment.player.confirmed.DLT",
            "payment.player.expired.DLT"}, groupId = "booking-service",
            containerFactory = "manualAckListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        record(record.topic(), record.key(), record.value());
        ack.acknowledge();
    }

    /** Count + log a dead-lettered payment event. Package-private so it's unit-testable without a broker. */
    void record(String topic, String key, String payload) {
        Counter.builder("booking.payment.deadletter.total")
                .description("payment.* events dead-lettered after retries — a booking state change that "
                        + "never applied (user may have paid / be owed a refund; needs manual replay)")
                .tag("topic", topic == null ? "unknown" : topic)
                .register(meterRegistry)
                .increment();
        log.error("[DLT] payment event dead-lettered after retries on {} — booking state NOT applied; "
                + "money may be at risk (paid-not-confirmed / refund-owed). key={} payload={}",
                topic, key, payload);
    }
}
