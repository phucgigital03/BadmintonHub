package com.badmintonhub.booking.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the payment DLT monitor — each dead letter bumps the alertable counter per topic. No broker. */
class PaymentDeadLetterMonitorTest {

    @Test
    void record_eachDeadLetter_incrementsCounterTaggedByTopic() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentDeadLetterMonitor monitor = new PaymentDeadLetterMonitor(registry);

        monitor.record("payment.player.confirmed.DLT", "evt-1", "{\"bookingId\":\"b1\"}");
        monitor.record("payment.player.confirmed.DLT", "evt-2", "{\"bookingId\":\"b2\"}");
        monitor.record("payment.proof.submitted.DLT", "evt-3", "{}");

        assertThat(registry.get("booking.payment.deadletter.total")
                .tag("topic", "payment.player.confirmed.DLT").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("booking.payment.deadletter.total")
                .tag("topic", "payment.proof.submitted.DLT").counter().count()).isEqualTo(1.0);
    }

    /**
     * The invariant that actually protects the alert: the counter for EVERY statically-known topic must
     * already exist at 0.0 <em>before</em> the first dead letter. The alert is
     * {@code increase(booking_payment_deadletter_total[5m]) > 0} — if the series is only born on the first
     * message it appears already at 1, there is no step to measure, and the very first dead letter is
     * silently missed (measured on a live cluster: msg #1 → no alert; msgs #2+#3 → "2 messages").
     *
     * <p>Topics are read back off the {@code @KafkaListener} annotation rather than re-listed here, so
     * adding a topic to the listener without pre-registering its counter fails this test immediately.</p>
     */
    @Test
    void everyListenerTopic_hasCounterPreRegisteredAtZero() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new PaymentDeadLetterMonitor(registry);

        String[] listenerTopics = PaymentDeadLetterMonitor.class
                .getMethod("onDeadLetter", ConsumerRecord.class, Acknowledgment.class)
                .getAnnotation(KafkaListener.class).topics();

        assertThat(listenerTopics).isNotEmpty();
        for (String topic : listenerTopics) {
            assertThat(registry.get("booking.payment.deadletter.total").tag("topic", topic).counter().count())
                    .as("counter for %s must exist at 0.0 BEFORE any message — otherwise increase() "
                            + "cannot see the first dead letter and the alert stays silent", topic)
                    .isEqualTo(0.0);
        }
    }

    @Test
    void record_nullTopic_countsUnderUnknown_doesNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentDeadLetterMonitor monitor = new PaymentDeadLetterMonitor(registry);

        monitor.record(null, "evt-x", "{}");

        assertThat(registry.get("booking.payment.deadletter.total")
                .tag("topic", "unknown").counter().count()).isEqualTo(1.0);
    }
}
