package com.badmintonhub.common.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String code, String message) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
