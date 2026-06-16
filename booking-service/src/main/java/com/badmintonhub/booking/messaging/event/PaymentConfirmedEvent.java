package com.badmintonhub.booking.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound payload of {@code payment.player.confirmed} (produced by payment-service). booking-service
 * only acts on {@code bookingId} (null for a MATCH_PLAYER payment → ignored). Mirrors the producer's
 * record; unknown fields are tolerated for forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentConfirmedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId,
        UUID userId,
        BigDecimal amount,
        String paymentType
) {}
