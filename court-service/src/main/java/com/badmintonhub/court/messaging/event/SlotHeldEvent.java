package com.badmintonhub.court.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mirror of booking-service's {@code booking.slot.held} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlotHeldEvent(
        String eventId,
        UUID bookingId,
        List<UUID> slotIds,
        LocalDateTime holdExpiresAt
) {}
