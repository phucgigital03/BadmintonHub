package com.badmintonhub.payment.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.payment.entity.enums.BankTransactionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A credit (money-in) line from a bank statement — the <em>ground truth</em> the AI reconciliation
 * (Day 10.5) checks a payment proof against. The payer writes the payment {@code order_code} (shown as
 * {@code #184}) in the transfer note, so {@code memo} carries it and the AI tool looks a transaction up
 * by {@code order_code} + {@code amount}.
 *
 * <p>{@code bankRef} (the bank's own transaction reference) is the natural dedupe key — re-importing the
 * same statement must not create duplicates, so it is {@code UNIQUE}. Unlike the partial index on
 * {@code payments}, this is a plain unique constraint, so Hibernate ({@code ddl-auto=update}) creates it
 * for this new table — no index-initializer needed.
 *
 * <p>Fields are flat statement data (no FK to {@code bank_accounts}); {@code source} marks the ingestion
 * path so a future SePay webhook can write the same table.
 */
@Entity
@Table(
        name = "bank_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_bank_transactions_bank_ref", columnNames = "bank_ref"),
        indexes = {
                @Index(name = "idx_bank_transactions_amount", columnList = "amount"),
                @Index(name = "idx_bank_transactions_transferred_at", columnList = "transferred_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BankTransaction extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bank_ref", nullable = false)
    private String bankRef; // bank's transaction reference — dedupe key (UNIQUE)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // credit (money-in) amount in VND

    @Column(name = "transferred_at", nullable = false)
    private LocalDateTime transferredAt; // when the transfer settled (statement date)

    @Column(name = "memo", length = 500)
    private String memo; // transfer description/content — carries the "#orderCode" the payer wrote

    @Column(name = "sender_name", length = 255)
    private String senderName; // counterparty name on the statement · nullable

    @Column(name = "account_number", length = 50)
    private String accountNumber; // counterparty account number · nullable

    @Column(name = "raw_line", columnDefinition = "text")
    private String rawLine; // original statement line · kept for audit / re-parse

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankTransactionSource source;
}
