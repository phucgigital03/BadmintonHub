package com.badmintonhub.payment.entity.enums;

/** Where a {@code bank_transactions} row came from. */
public enum BankTransactionSource {
    /** STAFF uploaded a bank statement (CSV) — the pilot ingestion path. */
    MANUAL_IMPORT,
    /** Pushed by a SePay webhook — extension point, not wired yet (same table, same {@code bank_ref} dedupe). */
    SEPAY
}
