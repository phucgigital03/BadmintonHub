package com.badmintonhub.payment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A transfer-screenshot uploaded against a payment, for STAFF review. {@code imageUrl} is the Cloudinary
 * URL (or a {@code local-fallback://} placeholder in dev). Review fields are set once STAFF acts.
 */
public record PaymentProofResponse(
        String imageUrl,
        LocalDateTime uploadedAt,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String reviewNote
) {}
