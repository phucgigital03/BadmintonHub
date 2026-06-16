package com.badmintonhub.payment.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code payment.refund.processed}. notification-service tells the payer their refund was
 * executed. {@code eventId} is the Kafka message key.
 */
public record RefundProcessedEvent(
        String eventId,
        UUID paymentId,
        UUID userId,
        BigDecimal amount
) {}
