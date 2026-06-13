package com.badmintonhub.escrow.repository;

import com.badmintonhub.escrow.entity.EscrowTransaction;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionStatus;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, UUID> {

    /** STAFF queues: pending settlements / refunds by type. */
    List<EscrowTransaction> findByTypeAndStatusOrderByCreatedAtAsc(EscrowTransactionType type,
                                                                   EscrowTransactionStatus status);

    /** Pending refund queue covers both HOST_REFUND and PLAYER_REFUND. */
    List<EscrowTransaction> findByTypeInAndStatusOrderByCreatedAtAsc(List<EscrowTransactionType> types,
                                                                     EscrowTransactionStatus status);

    /** All ledger entries of one account (escrow detail view). */
    List<EscrowTransaction> findByEscrow_IdOrderByCreatedAtAsc(UUID escrowId);
}
