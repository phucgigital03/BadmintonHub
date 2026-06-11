package com.badmintonhub.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Mirror of court-service {@code SlotResponse} (only the fields booking-service snapshots). {@code status}
 * is kept as a String to avoid coupling on court-service's enum; {@code price} is the pre-computed
 * per-cell snapshot (null if no pricing rule matched).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlotView(
        UUID id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        BigDecimal price
) {
    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }
}
