package com.badmintonhub.payment.messaging.event;

import com.badmintonhub.payment.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code payment.player.confirmed} / {@code payment.host.confirmed}. Carries every
 * cross-service reference so each consumer can pick the one it cares about (booking reads
 * {@code bookingId}; escrow reads {@code matchId}). {@code eventId} is the Kafka message key.
 */
public record PaymentConfirmedEvent(
        String eventId,
        UUID paymentId,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId,
        UUID userId,
        BigDecimal amount,
        PaymentType paymentType
) {}
