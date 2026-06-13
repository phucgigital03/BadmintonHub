package com.badmintonhub.payment.dto.request;

import com.badmintonhub.payment.entity.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Start a Bank-QR payment. Exactly one of {@code bookingId} / {@code matchId} / {@code enrollmentId} is
 * expected for the given {@code paymentType} (validated in the service). {@code amount} is the VND to
 * transfer — STAFF verifies the actual transfer against the uploaded proof before confirming.
 */
public record InitiatePaymentRequest(
        @NotNull PaymentType paymentType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId
) {}
