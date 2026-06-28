package com.badmintonhub.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One credit row a {@link BankStatementParser} extracted from a statement, before it is deduped and
 * persisted as a {@link com.badmintonhub.payment.entity.BankTransaction}. Parser-agnostic — the service
 * maps it to the entity and stamps the {@code source}.
 */
public record ParsedBankTransaction(
        String bankRef,
        BigDecimal amount,
        LocalDateTime transferredAt,
        String memo,
        String senderName,
        String accountNumber,
        String rawLine
) {}
