package com.badmintonhub.court.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/** Mirror of booking-service's {@code booking.slot.released} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlotReleasedEvent(
        String eventId,
        UUID bookingId,
        List<UUID> slotIds
) {}
