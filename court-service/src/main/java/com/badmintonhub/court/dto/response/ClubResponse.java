package com.badmintonhub.court.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ClubResponse(
        UUID id,
        String name,
        String address,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> images,
        BigDecimal rating,
        int totalReviews,
        boolean isActive
) {}
