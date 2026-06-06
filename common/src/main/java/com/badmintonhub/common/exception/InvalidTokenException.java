package com.badmintonhub.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
