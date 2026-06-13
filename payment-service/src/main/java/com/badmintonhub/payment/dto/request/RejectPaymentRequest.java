package com.badmintonhub.payment.dto.request;

import jakarta.validation.constraints.Size;

/** Optional reason STAFF gives when rejecting a payment proof. */
public record RejectPaymentRequest(
        @Size(max = 255) String reason
) {}
