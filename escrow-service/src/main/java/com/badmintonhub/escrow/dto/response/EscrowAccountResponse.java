package com.badmintonhub.escrow.dto.response;

import com.badmintonhub.escrow.entity.enums.EscrowAccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full escrow state of one match: the account plus its ledger entries. */
public record EscrowAccountResponse(
        UUID id,
        UUID matchId,
        UUID courtOwnerId,
        BigDecimal amount,
        BigDecimal releasedAmount,
        EscrowAccountStatus status,
        LocalDateTime settledAt,
        LocalDateTime createdAt,
        List<EscrowTransactionResponse> transactions
) {}
