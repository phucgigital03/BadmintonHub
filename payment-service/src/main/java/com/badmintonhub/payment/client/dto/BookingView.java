package com.badmintonhub.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Subset of booking-service's {@code BookingResponse} that payment-service needs from the
 * begin-payment handshake: the order id, its owner, status, and the authoritative {@code totalPrice}
 * (the amount to charge). Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingView(
        UUID id,
        UUID userId,
        String status,
        BigDecimal totalPrice
) {}
