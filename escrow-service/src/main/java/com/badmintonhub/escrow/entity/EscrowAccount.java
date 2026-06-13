package com.badmintonhub.escrow.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.escrow.entity.enums.EscrowAccountStatus;
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
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Escrow account for one match: holds the Host's upfront {@code court_price} and tracks how much has
 * been reimbursed back to the Host as Players pay. One match = one account ({@code match_id} UNIQUE).
 *
 * <p>{@code amount} is a snapshot of the deposited court_price (Never-Violate #9 — immutable).
 * {@code court_owner_id} is left null at HOLDING (the host-payment event doesn't carry it) and set when
 * the {@code match.completed} event arrives. All ids are cross-service UUIDs — no FK.</p>
 */
@Entity
@Table(
        name = "escrow_accounts",
        indexes = {
                @Index(name = "idx_escrow_accounts_match", columnList = "match_id", unique = true),
                @Index(name = "idx_escrow_accounts_status", columnList = "status")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class EscrowAccount extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID matchId; // ref matches.id · cross-service UUID · 1 match = 1 escrow account

    @Column(name = "court_owner_id", columnDefinition = "uuid")
    private UUID courtOwnerId; // ref users.id · cross-service UUID · nullable until match.completed

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // snapshot of deposited court_price — immutable

    @Column(name = "released_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal releasedAmount = BigDecimal.ZERO; // cumulative reimbursed to Host

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowAccountStatus status = EscrowAccountStatus.HOLDING;

    @Column(name = "settled_at")
    private LocalDateTime settledAt; // set when SETTLED or REFUNDED
}
