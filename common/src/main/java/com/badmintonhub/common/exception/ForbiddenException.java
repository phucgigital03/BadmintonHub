package com.badmintonhub.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String message) {
        super(code, message, HttpStatus.FORBIDDEN);
    }
}
