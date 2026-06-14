package com.badmintonhub.booking.messaging;

/** Kafka topic names produced by booking-service (consumed by court-service). */
public final class BookingTopics {

    /** A PENDING booking now holds these slots → court-service flips them AVAILABLE→RESERVED. */
    public static final String SLOT_HELD = "booking.slot.held";

    /** A booking was cancelled/expired → court-service flips its slots RESERVED→AVAILABLE. */
    public static final String SLOT_RELEASED = "booking.slot.released";

    /**
     * A payment was CONFIRMED for a booking that is already CANCELLED (money taken for a dead order) →
     * payment-service flags it as needing a manual refund. Compensating event (zombie-event pattern).
     */
    public static final String PAYMENT_ORPHANED = "booking.payment.orphaned";

    private BookingTopics() {}
}
