package com.badmintonhub.payment.messaging.event;

import java.util.UUID;

/**
 * Payload of {@code payment.proof.submitted}. {@code eventId} also travels as the Kafka message key
 * for consumer-side idempotency.
 */
public record ProofSubmittedEvent(
        String eventId,
        UUID paymentId,
        UUID userId,
        Long orderCode
) {}
