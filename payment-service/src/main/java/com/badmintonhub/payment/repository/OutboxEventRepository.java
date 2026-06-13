package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.OutboxEvent;
import com.badmintonhub.payment.entity.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest-first batch of un-published events for the publisher (bounded to avoid long transactions). */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /** Cleanup of already-published rows. */
    void deleteByStatusAndSentAtBefore(OutboxStatus status, LocalDateTime cutoff);
}
