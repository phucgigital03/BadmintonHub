package com.badmintonhub.court.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the slot DLT monitor — each dead letter bumps the alertable counter. No broker needed. */
class SlotDeadLetterMonitorTest {

    @Test
    void record_eachDeadLetter_incrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SlotDeadLetterMonitor monitor = new SlotDeadLetterMonitor(registry);

        monitor.record("slot-1", "{\"action\":\"RELEASED\"}");
        monitor.record("slot-2", "{\"action\":\"HELD\"}");

        assertThat(registry.get("court.slot.deadletter.total").counter().count()).isEqualTo(2.0);
    }

    @Test
    void counter_registeredAtZero_beforeAnyDeadLetter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new SlotDeadLetterMonitor(registry);

        assertThat(registry.get("court.slot.deadletter.total").counter().count()).isEqualTo(0.0);
    }
}
