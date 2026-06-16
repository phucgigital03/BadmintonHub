package com.badmintonhub.payment.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the booking-compensation DLT monitor — each dead letter bumps the counter per topic. No broker. */
class BookingDeadLetterMonitorTest {

    @Test
    void record_eachDeadLetter_incrementsCounterTaggedByTopic() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingDeadLetterMonitor monitor = new BookingDeadLetterMonitor(registry);

        monitor.record("booking.refund.required.DLT", "evt-1", "{\"refundAmount\":120000}");
        monitor.record("booking.refund.required.DLT", "evt-2", "{\"refundAmount\":60000}");
        monitor.record("booking.payment.orphaned.DLT", "evt-3", "{}");

        assertThat(registry.get("payment.booking.deadletter.total")
                .tag("topic", "booking.refund.required.DLT").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("payment.booking.deadletter.total")
                .tag("topic", "booking.payment.orphaned.DLT").counter().count()).isEqualTo(1.0);
    }

    @Test
    void record_nullTopic_countsUnderUnknown_doesNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingDeadLetterMonitor monitor = new BookingDeadLetterMonitor(registry);

        monitor.record(null, "evt-x", "{}");

        assertThat(registry.get("payment.booking.deadletter.total")
                .tag("topic", "unknown").counter().count()).isEqualTo(1.0);
    }
}
