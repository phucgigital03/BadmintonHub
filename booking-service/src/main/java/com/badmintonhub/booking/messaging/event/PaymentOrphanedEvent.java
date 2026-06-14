package com.badmintonhub.booking.messaging.event;

import java.util.UUID;

/**
 * Payload of {@code booking.payment.orphaned} — emitted when a {@code payment.player.confirmed} lands on a
 * booking that is already CANCELLED, so payment-service can flag the confirmed payment for a manual refund.
 * {@code eventId} also travels as the Kafka message key (idempotency).
 */
public record PaymentOrphanedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        String reason
) {}
