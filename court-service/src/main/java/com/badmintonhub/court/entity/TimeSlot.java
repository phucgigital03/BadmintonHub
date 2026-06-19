package com.badmintonhub.court.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.court.entity.enums.SlotStatus;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One bookable atomic slot (granularity = ô 30') on a physical {@link Court}. Holder back-refs
 * (booking/match/event/enrollment) are cross-service UUIDs — set when the slot is reserved.
 */
@Entity
@Table(
        name = "time_slots",
        indexes = @Index(name = "idx_slot_court_date_status", columnList = "court_id, date, status")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class TimeSlot extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic-lock guard against lost updates when two slot-hold events (held / released) for the same
     * slot are processed concurrently or out of order. The losing transaction gets an
     * {@code OptimisticLockException} → the Kafka error handler retries → it re-reads the fresh state and
     * the ownership guard decides correctly.
     */
    @Version
    @ColumnDefault("0")
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "blocked_by", columnDefinition = "uuid")
    private UUID blockedBy; // ref users.id · cross-service UUID · STAFF/ADMIN who blocked · nullable

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID eventId; // ref events.id · cross-service UUID · set when status=EVENT

    @Column(name = "match_id", columnDefinition = "uuid")
    private UUID matchId; // ref matches.id · cross-service UUID · set when RESERVED via match

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId; // ref bookings.id (HEADER) · cross-service UUID · N slots share 1 booking_id

    @Column(name = "enrollment_id", columnDefinition = "uuid")
    private UUID enrollmentId; // ref coach_enrollments.id · cross-service UUID · set when RESERVED via coaching
}
