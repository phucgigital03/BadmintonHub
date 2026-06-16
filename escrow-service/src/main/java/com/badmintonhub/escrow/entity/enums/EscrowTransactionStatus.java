package com.badmintonhub.escrow.entity.enums;

/**
 * Settlement state of one ledger entry. Reimbursements are recorded COMPLETED immediately (in-system
 * accounting); settlements/refunds are PENDING until STAFF executes the manual bank transfer.
 */
public enum EscrowTransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
