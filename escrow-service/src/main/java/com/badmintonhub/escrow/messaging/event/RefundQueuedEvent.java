package com.badmintonhub.escrow.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code payment.refund.queued} (consumed by notification-service + payment-service). One
 * event per cancelled match; {@code amount} = total queued refund (host remainder + players). STAFF
 * executes the actual bank transfers manually (Never-Violate #2). {@code eventId} = Kafka message key.
 */
public record RefundQueuedEvent(
        String eventId,
        UUID matchId,
        BigDecimal amount
) {}
