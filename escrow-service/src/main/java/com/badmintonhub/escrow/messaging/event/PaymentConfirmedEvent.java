package com.badmintonhub.escrow.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload escrow expects on {@code payment.host.confirmed} / {@code payment.player.confirmed} (produced
 * by payment-service). {@code userId} is the Host on a host payment, the paying Player on a player
 * payment. Unknown extra fields (e.g. {@code bookingId}, {@code paymentType}) are ignored on
 * deserialization, so this stays a tolerant subset of the producer contract.
 */
public record PaymentConfirmedEvent(
        UUID paymentId,
        UUID matchId,
        UUID userId,
        BigDecimal amount
) {}
