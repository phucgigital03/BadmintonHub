package com.badmintonhub.court.service;

import com.badmintonhub.court.dto.response.ClubGridResponse;
import com.badmintonhub.court.dto.response.SlotResponse;
import com.badmintonhub.court.entity.enums.Sport;

import java.time.LocalDate;
import java.util.UUID;

public interface SlotService {

    /** Generate 30-min AVAILABLE slots (05:00–22:00) for all active courts of a club over [from, to]. Idempotent per (court, date). Returns count created. */
    int generateForClub(UUID clubId, LocalDate from, LocalDate to);

    /** Same, for every active court in the system — used by the nightly scheduler. */
    int generateForAllActiveCourts(LocalDate from, LocalDate to);

    /** The visual day-booking grid: rows = courts (optionally filtered by sport), columns = 30-min cells with price. */
    ClubGridResponse getGrid(UUID clubId, LocalDate date, Sport sport);

    /** Single slot + price — for Feign lookups from booking/matchmaking-service. */
    SlotResponse getSlot(UUID courtId, UUID slotId);
}
