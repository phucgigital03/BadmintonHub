package com.badmintonhub.payment.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound payload of {@code booking.refund.required} (produced by booking-service): a CONFIRMED (paid)
 * booking was cancelled within the refund window — flag the matching payment for a manual refund and
 * carry the policy-computed {@code refundAmount} as the suggested transfer. Mirrors the producer's record;
 * unknown fields tolerated for forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundRequiredEvent(
        String eventId,
        UUID bookingId,
        BigDecimal refundAmount,
        String reason
) {}
