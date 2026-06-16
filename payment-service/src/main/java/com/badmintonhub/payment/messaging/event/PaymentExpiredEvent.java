package com.badmintonhub.payment.messaging.event;

import com.badmintonhub.payment.entity.enums.PaymentType;

import java.util.UUID;

/**
 * Payload of {@code payment.player.expired} / {@code payment.host.expired} (scheduler timeout or STAFF
 * reject). Booking releases the held slots; matchmaking cancels the match. {@code eventId} is the Kafka key.
 */
public record PaymentExpiredEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID userId,
        PaymentType paymentType
) {}
