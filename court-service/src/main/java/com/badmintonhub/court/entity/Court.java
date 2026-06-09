package com.badmintonhub.court.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.court.entity.enums.CourtType;
import com.badmintonhub.court.entity.enums.Sport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One physical court ("Sân N") belonging to a {@link Club}. Same-DB FK → @ManyToOne is fine.
 */
@Entity
@Table(name = "courts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Court extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "court_number", nullable = false)
    private String courtNumber; // e.g. "Sân 1"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourtType type;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
