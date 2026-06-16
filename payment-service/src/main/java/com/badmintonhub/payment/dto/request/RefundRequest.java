package com.badmintonhub.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** STAFF records a manual refund they executed via bank transfer. */
public record RefundRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(max = 100) String toBankName,
        @NotBlank @Size(max = 50) String toAccountNumber,
        @NotBlank @Size(max = 120) String toAccountName,
        @Size(max = 255) String refundNote
) {}
