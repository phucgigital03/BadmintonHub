package com.badmintonhub.court.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create a venue/club. {@code images} are pre-uploaded URLs stored as-is into {@code clubs.images}
 * (jsonb) — Cloudinary upload is deferred until credentials are configured.
 */
public record CreateClubRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String district,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> images
) {}
