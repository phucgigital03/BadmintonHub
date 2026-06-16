package com.badmintonhub.payment.entity.enums;

/**
 * What a payment pays for. Drives which Kafka topic the confirmation/expiry is published on and
 * which cross-service id ({@code bookingId} / {@code matchId} / {@code enrollmentId}) is set.
 */
public enum PaymentType {
    /** Court booking payment (UC_Visual_Day_Booking) — confirmation flows to booking-service. */
    BOOKING,
    /** Host pays full court_price upfront (Prepay model) — confirmation flows to matchmaking + escrow. */
    MATCH_HOST,
    /** Player pays price_per_person to join a match — confirmation flows to booking + escrow. */
    MATCH_PLAYER,
    /** Student pays to enroll with a coach. */
    COACH_ENROLLMENT,
    /** User buys an event ticket. */
    EVENT_TICKET
}
