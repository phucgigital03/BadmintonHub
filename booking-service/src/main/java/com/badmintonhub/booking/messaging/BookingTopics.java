package com.badmintonhub.booking.messaging;

/** Kafka topic names produced by booking-service (consumed by court-service). */
public final class BookingTopics {

    /** A PENDING booking now holds these slots → court-service flips them AVAILABLE→RESERVED. */
    public static final String SLOT_HELD = "booking.slot.held";

    /** A booking was cancelled/expired → court-service flips its slots RESERVED→AVAILABLE. */
    public static final String SLOT_RELEASED = "booking.slot.released";

    private BookingTopics() {}
}
