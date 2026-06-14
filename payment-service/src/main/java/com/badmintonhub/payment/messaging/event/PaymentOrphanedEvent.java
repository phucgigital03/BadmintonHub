package com.badmintonhub.payment.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Inbound payload of {@code booking.payment.orphaned} (produced by booking-service): a CONFIRMED payment
 * whose booking is already CANCELLED — flag it for a manual refund. Mirrors the producer's record;
 * unknown fields tolerated for forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentOrphanedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        String reason
) {}
