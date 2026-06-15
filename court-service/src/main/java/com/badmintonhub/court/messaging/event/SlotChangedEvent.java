package com.badmintonhub.court.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inbound payload of {@code booking.slot.changed} (produced by booking-service): one slot's hold change.
 * The Kafka key is the {@code slotId} (for ordering), so idempotency dedupes on {@code eventId} here in the
 * payload, NOT on the record key. Unknown fields tolerated for forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlotChangedEvent(
        String eventId,
        SlotAction action,
        UUID bookingId,
        UUID slotId,
        LocalDateTime holdExpiresAt
) {}
