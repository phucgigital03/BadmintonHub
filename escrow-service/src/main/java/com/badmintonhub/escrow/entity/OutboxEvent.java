package com.badmintonhub.escrow.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import com.badmintonhub.escrow.entity.enums.OutboxStatus;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox row. Written in the <b>same {@code @Transactional}</b> as the escrow change it
 * describes ({@code escrow.host.reimbursed} on player reimbursement, {@code payment.refund.queued} on
 * cancellation), so the event is never lost and never published for a rolled-back change. The
 * {@code OutboxPublisherScheduler} polls PENDING rows, sends them to Kafka, then marks them SENT.
 *
 * <p>{@code msgKey} = the event UUID — used as the Kafka message key so the consumer can dedupe
 * (idempotency) and carried in {@code payload} too.</p>
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class OutboxEvent extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "msg_key", nullable = false)
    private String msgKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
