package com.badmintonhub.booking.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payload of {@code booking.slot.changed} — ONE message per slot, with the Kafka key = {@code slotId} so
 * all changes to a slot are totally ordered on one partition (released never overtakes held). {@code eventId}
 * is the idempotency id court-service dedupes on (read from the payload, NOT the Kafka key, since the key is
 * the slotId). {@code holdExpiresAt} is set only for {@link SlotAction#HELD}.
 */
public record SlotChangedEvent(
        String eventId,
        SlotAction action,
        UUID bookingId,
        UUID slotId,
        LocalDateTime holdExpiresAt
) {}
