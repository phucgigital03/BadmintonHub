package com.badmintonhub.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/** Mirror of court-service {@code CourtSlotsResponse}: a grid row (court header + its 30-min cells). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourtSlotsView(
        UUID id,
        String courtNumber,
        String sport,
        String type,
        List<SlotView> slots
) {}
