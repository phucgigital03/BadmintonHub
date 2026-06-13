package com.badmintonhub.payment.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;
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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Bank-QR payment. Exactly one of {@code bookingId} / {@code matchId} / {@code enrollmentId} (or none
 * for an event ticket — referenced from event_tickets) identifies what it pays for, per {@code paymentType}.
 * Those are cross-service UUIDs (no FK). {@code orderCode} is a human-friendly serial the payer writes in
 * the transfer note (shown as {@code #184}).
 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_order_code", columnNames = "order_code"),
        indexes = {
                @Index(name = "idx_payments_user", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_booking", columnList = "booking_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Payment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly serial shown on screen + written in the transfer note. Postgres sequence. */
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_order_seq")
    @SequenceGenerator(name = "payment_order_seq", sequenceName = "payment_order_seq", allocationSize = 1)
    @Column(name = "order_code", nullable = false, updatable = false)
    private Long orderCode;

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId; // ref bookings.id · cross-service UUID · nullable (set for BOOKING)

    @Column(name = "match_id", columnDefinition = "uuid")
    private UUID matchId; // ref matches.id · cross-service UUID · nullable (set for MATCH_*)

    @Column(name = "enrollment_id", columnDefinition = "uuid")
    private UUID enrollmentId; // ref coach_enrollments.id · cross-service UUID · nullable

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId; // ref users.id · cross-service UUID · the payer

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount; // within-db FK · which account to display

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // VND to transfer

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount; // VND refunded · set on manual refund

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // countdown deadline — PENDING released by scheduler past this

    @Column(name = "confirmed_by", columnDefinition = "uuid")
    private UUID confirmedBy; // ref users.id · cross-service UUID · STAFF/ADMIN who confirmed · nullable

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "reject_reason")
    private String rejectReason; // set when STAFF rejects the proof
}
