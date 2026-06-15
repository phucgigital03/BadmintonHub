package com.badmintonhub.court.messaging.event;

/** Mirror of booking-service's slot action. */
public enum SlotAction {
    /** Flip the slot AVAILABLE → RESERVED for the booking. */
    HELD,
    /** Flip the slot RESERVED → AVAILABLE (the booking released it). */
    RELEASED
}
