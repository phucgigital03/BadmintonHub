package com.badmintonhub.payment.messaging.event;

import java.util.UUID;

/**
 * Payload of {@code payment.proof.submitted}. {@code eventId} also travels as the Kafka message key
 * for consumer-side idempotency. {@code bookingId} / {@code matchId} let a consumer route the event —
 * booking-service uses {@code bookingId} to pause the booking hold once proof exists.
 */
public record ProofSubmittedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID userId,
        Long orderCode
) {}
