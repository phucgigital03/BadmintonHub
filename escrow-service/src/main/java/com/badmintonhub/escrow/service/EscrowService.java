package com.badmintonhub.escrow.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Escrow ledger operations driven by Kafka events. Each method runs inside the consumer's
 * {@code @Transactional} (idempotency row + ledger change commit together). Methods that need an
 * existing account throw {@link IllegalStateException} when none exists yet (out-of-order delivery) so
 * the consumer retries → DLT rather than silently losing money.
 */
public interface EscrowService {

    /** payment.host.confirmed → open the escrow account HOLDING and record the HOST_DEPOSIT. */
    void recordHostDeposit(UUID matchId, UUID hostId, BigDecimal amount, UUID paymentId);

    /** payment.player.confirmed → reimburse the Host this player's share (account → PARTIALLY_RELEASED). */
    void recordPlayerReimbursement(UUID matchId, UUID playerId, BigDecimal amount, UUID paymentId);

    /** match.completed → record the PENDING court-owner settlement (account → SETTLED). */
    void settle(UUID matchId, UUID courtOwnerId);

    /** match.cancelled → queue PENDING host + player refunds (account → REFUNDED). */
    void refund(UUID matchId);
}
