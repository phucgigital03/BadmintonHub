package com.badmintonhub.court.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A venue / club (CLB) — the single sport complex the platform manages. Owns N {@link Court}s.
 * Soft-deactivation via {@code is_active} (court-service is not a soft-delete table — only users/coaches are).
 */
@Entity
@Table(name = "clubs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Club extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy; // ref users.id · cross-service UUID (STAFF/ADMIN who created the club)

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String district;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> images; // Cloudinary URL array

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO; // avg of club_reviews

    @Column(name = "total_reviews", nullable = false)
    private int totalReviews = 0; // denormalized counter
}
