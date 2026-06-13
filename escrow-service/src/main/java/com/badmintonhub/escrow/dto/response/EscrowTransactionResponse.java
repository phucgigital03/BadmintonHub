package com.badmintonhub.escrow.dto.response;

import com.badmintonhub.escrow.entity.enums.EscrowTransactionStatus;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One escrow ledger entry — also the row shape STAFF sees in the settlement / refund queues. */
public record EscrowTransactionResponse(
        UUID id,
        UUID matchId,
        EscrowTransactionType type,
        UUID fromPartyId,
        UUID toPartyId,
        BigDecimal amount,
        UUID referencePaymentId,
        EscrowTransactionStatus status,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {}
