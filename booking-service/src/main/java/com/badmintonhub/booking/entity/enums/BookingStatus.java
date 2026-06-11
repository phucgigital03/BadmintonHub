package com.badmintonhub.booking.entity.enums;

/**
 * Header state machine: PENDING → CONFIRMED → COMPLETED, with CANCELLED reachable from
 * PENDING/CONFIRMED. The whole order transitions atomically (one payment, all items or none).
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
