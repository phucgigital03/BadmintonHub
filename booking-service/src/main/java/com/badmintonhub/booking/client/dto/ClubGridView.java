package com.badmintonhub.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/** Mirror of court-service {@code ClubGridResponse}: the day grid (court rows × 30-min cells). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubGridView(
        LocalDate date,
        String dayType,
        List<CourtSlotsView> courts
) {}
