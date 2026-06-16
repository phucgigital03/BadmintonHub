package com.badmintonhub.payment.entity;

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
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A transfer-screenshot uploaded against a {@link Payment}. {@code imageUrl} is the Cloudinary URL
 * (or a local-fallback placeholder when Cloudinary keys are absent). STAFF review fields are set when
 * the proof is reviewed.
 */
@Entity
@Table(
        name = "payment_proofs",
        indexes = @Index(name = "idx_payment_proofs_payment", columnList = "payment_id")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PaymentProof extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "uploaded_by", nullable = false, columnDefinition = "uuid")
    private UUID uploadedBy; // ref users.id · cross-service UUID

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "reviewed_by", columnDefinition = "uuid")
    private UUID reviewedBy; // ref users.id · cross-service UUID · STAFF/ADMIN · nullable

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note")
    private String reviewNote; // STAFF comment, e.g. amount mismatch
}
