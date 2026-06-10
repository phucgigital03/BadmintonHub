package com.badmintonhub.court.dto.response;

import com.badmintonhub.court.entity.enums.DayType;

import java.time.LocalDate;
import java.util.List;

/** The visual day-booking grid: rows = courts (Sân), columns = 30-min cells for {@code date}. */
public record ClubGridResponse(
        LocalDate date,
        DayType dayType,
        List<CourtSlotsResponse> courts
) {}
