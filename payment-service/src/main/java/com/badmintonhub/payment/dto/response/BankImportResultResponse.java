package com.badmintonhub.payment.dto.response;

/**
 * Outcome of a bank-statement import. {@code inserted} = new rows stored; {@code skipped} = rows already
 * present (deduped by {@code bank_ref}, in-file or already-imported); {@code totalRows} = credit rows parsed.
 */
public record BankImportResultResponse(int inserted, int skipped, int totalRows) {}
