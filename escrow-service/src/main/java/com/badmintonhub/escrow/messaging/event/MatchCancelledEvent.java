package com.badmintonhub.escrow.messaging.event;

import java.util.UUID;

/**
 * Payload escrow expects on {@code match.cancelled} (produced by matchmaking-service). {@code reason} is
 * informational; escrow refunds the Host the un-reimbursed remainder and each paying Player their share.
 */
public record MatchCancelledEvent(
        UUID matchId,
        String reason
) {}
