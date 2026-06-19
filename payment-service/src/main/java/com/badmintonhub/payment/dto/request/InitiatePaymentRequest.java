package com.badmintonhub.payment.dto.request;

import com.badmintonhub.payment.entity.enums.PaymentType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Start a Bank-QR payment. Exactly one of {@code bookingId} / {@code matchId} / {@code enrollmentId} is
 * expected for the given {@code paymentType} (validated in the service).
 * <p>
 * {@code amount} is optional on the wire: for {@code BOOKING} it is derived server-side from
 * {@code booking.totalPrice} via the begin-payment handshake (the client value is never trusted). Only the
 * other payment types use the client-sent {@code amount}, which the service then requires to be {@code > 0}.
 */
public record InitiatePaymentRequest(
        @NotNull PaymentType paymentType,
        BigDecimal amount,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId
) {}
