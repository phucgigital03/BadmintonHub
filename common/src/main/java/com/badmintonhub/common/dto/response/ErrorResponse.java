package com.badmintonhub.common.dto.response;

import java.time.Instant;

public record ErrorResponse(String code, String message, String timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now().toString());
    }
}
