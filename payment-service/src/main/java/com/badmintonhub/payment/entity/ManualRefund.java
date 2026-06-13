package com.badmintonhub.payment.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.payment.entity.enums.RefundMethod;
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
 * Immutable record of a refund STAFF executed manually (a real bank transfer). There is no automated
 * refund — STAFF transfers the money, then records the recipient details + amount here.
 */
@Entity
@Table(
        name = "manual_refunds",
        indexes = @Index(name = "idx_manual_refunds_payment", columnList = "payment_id")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ManualRefund extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // VND refunded

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false)
    private RefundMethod refundMethod = RefundMethod.BANK_TRANSFER;

    @Column(name = "to_bank_name", nullable = false)
    private String toBankName;

    @Column(name = "to_account_number", nullable = false)
    private String toAccountNumber;

    @Column(name = "to_account_name", nullable = false)
    private String toAccountName;

    @Column(name = "refund_note")
    private String refundNote;

    @Column(name = "processed_by", nullable = false, columnDefinition = "uuid")
    private UUID processedBy; // ref users.id · cross-service UUID · STAFF/ADMIN

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
