package com.badmintonhub.booking.messaging;

/** Kafka topic names produced by booking-service (consumed by court-service / payment-service). */
public final class BookingTopics {

    /**
     * A slot's hold state changed (one message PER slot, keyed by slotId) → court-service flips it
     * AVAILABLE↔RESERVED per the {@code action} (HELD / RELEASED). Keying by slotId puts every change to
     * the same slot on one partition, so held and released for that slot are consumed in order (released
     * never overtakes held) — which is what stops a slot from getting stuck RESERVED on a cancelled booking.
     */
    public static final String SLOT_CHANGED = "booking.slot.changed";

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
