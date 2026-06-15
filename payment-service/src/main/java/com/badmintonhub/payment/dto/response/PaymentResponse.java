package com.badmintonhub.payment.dto.response;

import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment view for the Bank-QR screen: status + amount + the active account's details so the frontend
 * can render the transfer info, QR image, and countdown. {@code orderCode} is the display string
 * ({@code "#184"}) the payer writes in the transfer note.
 */
public record PaymentResponse(
        UUID id,
        String orderCode,
        PaymentType paymentType,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal refundAmount,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId,
        UUID userId,
        LocalDateTime expiresAt,
        String bankName,
        String accountNumber,
        String accountName,
        String qrImageUrl,
        LocalDateTime createdAt,
        boolean refundRequired,
        BigDecimal refundRequiredAmount
) {}
