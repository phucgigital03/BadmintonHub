package com.badmintonhub.payment.dto.response;

import java.util.UUID;

/** The active bank account shown on the payment screen. */
public record BankAccountResponse(
        UUID id,
        String bankName,
        String accountNumber,
        String accountName,
        String qrImageUrl
) {}
