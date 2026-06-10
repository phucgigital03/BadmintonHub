package com.badmintonhub.court.dto.response;

import com.badmintonhub.court.entity.enums.CourtType;
import com.badmintonhub.court.entity.enums.Sport;

import java.util.List;
import java.util.UUID;

/** One grid row: a court header + its ordered 30-min slots for the requested date. */
public record CourtSlotsResponse(
        UUID id,
        String courtNumber,
        Sport sport,
        CourtType type,
        List<SlotResponse> slots
) {}
