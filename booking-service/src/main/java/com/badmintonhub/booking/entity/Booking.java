package com.badmintonhub.booking.entity;

import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;
import com.badmintonhub.common.entity.BaseAuditEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Booking order HEADER. One booking = one payment = N {@link BookingItem} (atomic 30-min cells).
 * {@code total_price} and {@code earliest_start_time} are snapshots frozen at creation; the order is
 * cancelled atomically (refund % is computed from {@code earliest_start_time}).
 *
 * <p>{@code userId} / {@code clubId} are cross-service UUIDs (user-service / court-service) — no FK.</p>
 */
@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_user", columnList = "user_id"),
                @Index(name = "idx_bookings_status", columnList = "status"),
                @Index(name = "idx_bookings_club_date", columnList = "club_id, booking_date")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Booking extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId; // ref users.id · cross-service UUID · who placed the booking

    @Column(name = "club_id", nullable = false, columnDefinition = "uuid")
    private UUID clubId; // ref clubs.id · cross-service UUID · single venue per order

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType = CustomerType.WALK_IN;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice; // snapshot = SUM(booking_items.price)

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount; // set on cancellation per policy

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "earliest_start_time", nullable = false)
    private LocalDateTime earliestStartTime; // snapshot of earliest item start — cancellation policy anchor

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt; // PENDING hold deadline — HoldExpiryScheduler releases slots past this

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_by", columnDefinition = "uuid")
    private UUID cancelledBy; // ref users.id · cross-service UUID · who cancelled · nullable

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
