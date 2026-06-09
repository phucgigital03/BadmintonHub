package com.badmintonhub.court.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

/**
 * A review of a {@link Club} (venue). {@code club} is a same-DB FK (clubs live in court_db); only
 * userId / bookingId / flaggedBy are cross-service UUIDs. One review per booking (unique).
 */
@Entity
@Table(name = "club_reviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ClubReview extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId; // ref users.id · cross-service UUID

    @Column(name = "booking_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID bookingId; // ref bookings.id · cross-service UUID · 1 review per booking

    @Column(nullable = false, columnDefinition = "smallint check (rating between 1 and 5)")
    private Short rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "is_flagged", nullable = false)
    private boolean isFlagged = false; // STAFF/ADMIN can flag abuse

    @Column(name = "flagged_by", columnDefinition = "uuid")
    private UUID flaggedBy; // ref users.id · cross-service UUID · nullable
}
