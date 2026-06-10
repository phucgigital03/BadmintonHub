package com.badmintonhub.court.dto.request;

import com.badmintonhub.court.entity.enums.CourtType;
import com.badmintonhub.court.entity.enums.Sport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Add one physical court ("Sân N") to a club. */
public record CreateCourtRequest(
        @NotBlank String courtNumber,
        @NotNull Sport sport,
        @NotNull CourtType type
) {}
