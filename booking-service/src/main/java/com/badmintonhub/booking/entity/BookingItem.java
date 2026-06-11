package com.badmintonhub.booking.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One atomic 30-min line item of a {@link Booking}. {@code slotId} is a cross-service UUID
 * (court_db time_slots) and is <b>UNIQUE</b> — the DB-level backstop against double-booking a slot
 * (the 5s Redis lock guards the creation race; this constraint holds for the whole active order).
 * {@code courtName} / {@code startTime} / {@code endTime} / {@code price} are snapshots frozen at booking time.
 */
@Entity
@Table(
        name = "booking_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_items_slot", columnNames = "slot_id"),
        indexes = @Index(name = "idx_booking_items_booking", columnList = "booking_id")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BookingItem extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "court_id", nullable = false, columnDefinition = "uuid")
    private UUID courtId; // ref courts.id · cross-service UUID

    @Column(name = "slot_id", nullable = false, columnDefinition = "uuid")
    private UUID slotId; // ref time_slots.id · cross-service UUID · UNIQUE (1 item ↔ 1 slot)

    @Column(name = "court_name", nullable = false)
    private String courtName; // snapshot, e.g. "Sân 2"

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime; // snapshot

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime; // snapshot

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price; // snapshot of court_pricing_rules ÷ 2 for this 30-min cell
}
