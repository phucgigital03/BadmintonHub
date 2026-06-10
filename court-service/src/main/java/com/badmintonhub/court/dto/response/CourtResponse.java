package com.badmintonhub.court.dto.response;

import com.badmintonhub.court.entity.enums.CourtType;
import com.badmintonhub.court.entity.enums.Sport;

import java.util.UUID;

public record CourtResponse(
        UUID id,
        UUID clubId,
        String courtNumber,
        Sport sport,
        CourtType type,
        boolean isActive
) {}
