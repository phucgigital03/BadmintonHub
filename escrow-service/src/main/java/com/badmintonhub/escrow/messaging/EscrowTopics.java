package com.badmintonhub.escrow.messaging;

/** Kafka topics escrow-service consumes and produces (see kafka-patterns.md topic registry). */
public final class EscrowTopics {

    // --- consumed ---
    /** Host paid court_price upfront → open escrow account HOLDING. */
    public static final String PAYMENT_HOST_CONFIRMED = "payment.host.confirmed";
    /** A player paid their share → reimburse Host proportionally. */
    public static final String PAYMENT_PLAYER_CONFIRMED = "payment.player.confirmed";
    /** Match finished → settle court_price to the court owner (STAFF executes the transfer). */
    public static final String MATCH_COMPLETED = "match.completed";
    /** Match cancelled → queue host/player refunds (STAFF executes the transfers). */
    public static final String MATCH_CANCELLED = "match.cancelled";

    // --- produced (via Outbox) ---
    /** Host was reimbursed a player's share → notify. */
    public static final String HOST_REIMBURSED = "escrow.host.reimbursed";
    /** Refunds queued for a cancelled match → notify + open manual refund queue. */
    public static final String REFUND_QUEUED = "payment.refund.queued";

    private EscrowTopics() {}
}
