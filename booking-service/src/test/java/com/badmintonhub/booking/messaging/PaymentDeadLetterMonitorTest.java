package com.badmintonhub.booking.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

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

    @Test
    void record_nullTopic_countsUnderUnknown_doesNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentDeadLetterMonitor monitor = new PaymentDeadLetterMonitor(registry);

        monitor.record(null, "evt-x", "{}");

        assertThat(registry.get("booking.payment.deadletter.total")
                .tag("topic", "unknown").counter().count()).isEqualTo(1.0);
    }
}
