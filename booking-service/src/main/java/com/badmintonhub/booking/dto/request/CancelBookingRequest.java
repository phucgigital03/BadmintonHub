package com.badmintonhub.booking.dto.request;

import jakarta.validation.constraints.Size;

/** Optional reason for a cancellation (recorded on the header). */
public record CancelBookingRequest(
        @Size(max = 255) String reason
) {}
