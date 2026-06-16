package com.badmintonhub.payment.entity.enums;

/**
 * Payment lifecycle (Bank QR + manual STAFF confirm — no payment gateway).
 * <pre>
 * PENDING → PROOF_SUBMITTED → CONFIRMED
 *                           ↘ EXPIRED   (scheduler timeout OR STAFF reject)
 * CONFIRMED → REFUNDED       (manual refund by STAFF)
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PROOF_SUBMITTED,
    CONFIRMED,
    EXPIRED,
    REFUNDED
}
