package com.badmintonhub.escrow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Idempotency guard (Never-Violate #5) for the escrow Kafka consumers. Before mutating an escrow
 * account, the handler checks this table by the event's UUID (the Kafka message key) and inserts after
 * a successful handle so a redelivery is a no-op. A daily scheduler purges rows older than 7 days.
 */
@Entity
@Table(name = "escrow_processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
    }
}
