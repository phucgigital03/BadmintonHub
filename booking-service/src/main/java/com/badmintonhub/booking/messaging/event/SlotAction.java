package com.badmintonhub.booking.messaging.event;

/** What a {@link SlotChangedEvent} tells court-service to do with the slot. */
public enum SlotAction {
    /** PENDING booking now holds the slot → court flips AVAILABLE → RESERVED. */
    HELD,
    /** Booking cancelled/expired → court flips RESERVED → AVAILABLE. */
    RELEASED
}
