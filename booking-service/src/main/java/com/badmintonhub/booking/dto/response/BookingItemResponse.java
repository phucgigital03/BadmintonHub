package com.badmintonhub.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/** One line item (snapshot) of a booking order. */
public record BookingItemResponse(
        UUID id,
        UUID courtId,
        UUID slotId,
        String courtName,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price
) {}
