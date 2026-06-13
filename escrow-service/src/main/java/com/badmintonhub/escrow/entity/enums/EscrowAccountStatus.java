package com.badmintonhub.escrow.entity.enums;

/**
 * Lifecycle of the court-fee escrow held for one match.
 * <pre>
 * HOLDING → PARTIALLY_RELEASED → SETTLED   (match COMPLETED — owner gets court_price)
 *         ↘                    ↘ REFUNDED   (match CANCELLED — host/players refunded)
 * </pre>
 */
public enum EscrowAccountStatus {
    HOLDING,
    PARTIALLY_RELEASED,
    SETTLED,
    REFUNDED
}
