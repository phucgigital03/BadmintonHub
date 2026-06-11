package com.badmintonhub.booking.messaging.event;

import java.util.List;
import java.util.UUID;

/** Payload of {@code booking.slot.released}. {@code eventId} also travels as the Kafka message key. */
public record SlotReleasedEvent(
        String eventId,
        UUID bookingId,
        List<UUID> slotIds
) {}
