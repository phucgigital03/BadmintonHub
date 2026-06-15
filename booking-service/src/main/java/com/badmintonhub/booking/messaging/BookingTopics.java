package com.badmintonhub.booking.messaging;

/** Kafka topic names produced by booking-service (consumed by court-service / payment-service). */
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

    /**
     * A CONFIRMED (already-paid) booking was cancelled within the refund window → payment-service flags
     * the matching payment for a manual refund and carries the policy-computed amount. Without this the
     * refund tier computed in booking-service would be a dead end (the user would silently lose money).
     */
    public static final String REFUND_REQUIRED = "booking.refund.required";

    private BookingTopics() {}
}
