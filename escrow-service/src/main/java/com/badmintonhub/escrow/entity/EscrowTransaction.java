package com.badmintonhub.escrow.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionStatus;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One ledger entry against an {@link EscrowAccount} (deposit, reimbursement, settlement, refund). The
 * account FK is within escrow_db (normal {@code @ManyToOne}); the party / payment references are
 * cross-service UUIDs with no FK. Settlement / refund rows are PENDING until STAFF executes the manual
 * bank transfer; deposits / reimbursements are recorded COMPLETED in-system.
 */
@Entity
@Table(
        name = "escrow_transactions",
        indexes = {
                @Index(name = "idx_escrow_tx_escrow", columnList = "escrow_id"),
                @Index(name = "idx_escrow_tx_type_status", columnList = "type, status")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class EscrowTransaction extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escrow_id", nullable = false)
    private EscrowAccount escrow; // within-db FK → escrow_accounts.id

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowTransactionType type;

    @Column(name = "from_party_id", columnDefinition = "uuid")
    private UUID fromPartyId; // ref users.id or system · cross-service UUID · nullable

    @Column(name = "to_party_id", columnDefinition = "uuid")
    private UUID toPartyId; // ref users.id or system · cross-service UUID · nullable

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // VND

    @Column(name = "reference_payment_id", columnDefinition = "uuid")
    private UUID referencePaymentId; // ref payments.id · cross-service UUID · the bank payment that triggered this · nullable

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowTransactionStatus status = EscrowTransactionStatus.PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
