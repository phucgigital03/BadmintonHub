package com.badmintonhub.escrow.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code escrow.host.reimbursed} (consumed by notification-service). {@code eventId} also
 * travels as the Kafka message key for consumer-side idempotency.
 */
public record HostReimbursedEvent(
        String eventId,
        UUID matchId,
        UUID hostId,
        BigDecimal amount
) {}
