package com.badmintonhub.escrow.entity.enums;

/** Transactional-outbox row state: PENDING until the publisher confirms the Kafka send → SENT. */
public enum OutboxStatus {
    PENDING,
    SENT
}
