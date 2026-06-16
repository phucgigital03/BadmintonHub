package com.badmintonhub.escrow.messaging.event;

import java.util.UUID;

/**
 * Payload escrow expects on {@code match.completed} (produced by matchmaking-service). {@code courtOwnerId}
 * is the venue owner who receives the court_price settlement — escrow records it now (it is null at
 * HOLDING because the host-payment event does not carry it).
 */
public record MatchCompletedEvent(
        UUID matchId,
        UUID courtOwnerId
) {}
