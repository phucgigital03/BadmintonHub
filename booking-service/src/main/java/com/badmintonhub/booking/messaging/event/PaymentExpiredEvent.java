package com.badmintonhub.booking.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Inbound payload of {@code payment.player.expired} (produced by payment-service). booking-service
 * cancels the PENDING booking + releases its slots when {@code bookingId} is set (null → ignored).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentExpiredEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID userId,
        String paymentType
) {}
