package com.badmintonhub.court.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Authoritative court price — multi-dimensional (club + sport × day type × time window × customer type).
 * One row per dimension (unique constraint). Price applies to all courts of that sport in the club.
 */
@Entity
@Table(
        name = "court_pricing_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pricing_dimension",
                columnNames = {"club_id", "sport", "day_type", "start_time", "customer_type"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class CourtPricingRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    @Column(name = "price_per_hour", nullable = false)
    private BigDecimal pricePerHour; // VND
}
