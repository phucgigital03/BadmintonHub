package com.badmintonhub.booking.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code booking.refund.required} — emitted when a CONFIRMED (already-paid) booking is cancelled
 * within the refund window. payment-service flags the matching payment for a manual refund and uses
 * {@code refundAmount} (the policy-computed tier) as the suggested transfer amount, so STAFF doesn't have
 * to recompute it. {@code eventId} also travels as the Kafka message key (idempotency).
 */
public record RefundRequiredEvent(
        String eventId,
        UUID bookingId,
        BigDecimal refundAmount,
        String reason
) {}
