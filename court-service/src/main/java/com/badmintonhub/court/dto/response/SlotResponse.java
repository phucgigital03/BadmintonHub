package com.badmintonhub.court.dto.response;

import com.badmintonhub.court.entity.enums.SlotStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One 30-min cell in the booking grid. {@code price} is the snapshot-at-read price for this cell
 * (matching court_pricing_rules ÷ 2 for the 30-min span); null when no rule matches.
 */
public record SlotResponse(
        UUID id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status,
        BigDecimal price,
        UUID eventId,
        UUID bookingId,
        UUID matchId,
        UUID enrollmentId
) {}
