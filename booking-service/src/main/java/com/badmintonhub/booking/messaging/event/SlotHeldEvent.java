package com.badmintonhub.booking.messaging.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code booking.slot.held}. {@code eventId} also travels as the Kafka message key for
 * consumer-side idempotency. {@code holdExpiresAt} is informational (court reuses RESERVED; expiry is
 * owned by booking-service).
 */
public record SlotHeldEvent(
        String eventId,
        UUID bookingId,
        List<UUID> slotIds,
        LocalDateTime holdExpiresAt
) {}
