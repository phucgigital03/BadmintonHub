package com.badmintonhub.payment.messaging;

/**
 * Kafka topic names produced by payment-service. All are published via the transactional Outbox
 * (never directly), so a topic is only emitted for a committed payment change.
 */
public final class PaymentTopics {

    /** Proof uploaded → notification-service pings STAFF to review. */
    public static final String PROOF_SUBMITTED = "payment.proof.submitted";

    /** MATCH_HOST payment confirmed → matchmaking + escrow. */
    public static final String HOST_CONFIRMED = "payment.host.confirmed";

    /** BOOKING / MATCH_PLAYER payment confirmed → booking + escrow. */
    public static final String PLAYER_CONFIRMED = "payment.player.confirmed";

    /** MATCH_HOST payment expired/rejected → matchmaking. */
    public static final String HOST_EXPIRED = "payment.host.expired";

    /** BOOKING / MATCH_PLAYER payment expired/rejected → booking (+ matchmaking). */
    public static final String PLAYER_EXPIRED = "payment.player.expired";

    /** Manual refund recorded → notification-service tells the user. */
    public static final String REFUND_PROCESSED = "payment.refund.processed";

    private PaymentTopics() {}
}
