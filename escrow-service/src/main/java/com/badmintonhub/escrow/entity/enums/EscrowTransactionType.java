package com.badmintonhub.escrow.entity.enums;

/** Ledger entry kind in {@code escrow_transactions} (per ERD escrow_db). */
public enum EscrowTransactionType {
    /** Host's upfront court_price into escrow (on payment.host.confirmed). */
    HOST_DEPOSIT,
    /** A player's share reimbursed to the Host (on payment.player.confirmed). */
    PLAYER_REIMBURSEMENT,
    /** Court owner receives court_price when the match COMPLETED (STAFF settles the bank transfer). */
    COURT_OWNER_SETTLEMENT,
    /** Host refund on match cancellation (STAFF executes the bank transfer). */
    HOST_REFUND,
    /** Player refund on match cancellation (STAFF executes the bank transfer). */
    PLAYER_REFUND
}
