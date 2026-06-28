package com.badmintonhub.payment.dto.response;

import com.badmintonhub.payment.entity.enums.BankTransactionSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A bank-statement credit row returned by the AI lookup. */
public record BankTransactionResponse(
        UUID id,
        String bankRef,
        BigDecimal amount,
        LocalDateTime transferredAt,
        String memo,
        String senderName,
        String accountNumber,
        BankTransactionSource source
) {}
