package com.badmintonhub.booking.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Inbound payload of {@code payment.proof.submitted} (produced by payment-service). booking-service
 * pauses the booking hold ({@code hold_expires_at = null}) when {@code bookingId} is set (null → ignored),
 * so HoldExpiryScheduler stops auto-cancelling once proof exists — STAFF confirm/reject decides the outcome.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentProofSubmittedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID userId,
        Long orderCode
) {}
