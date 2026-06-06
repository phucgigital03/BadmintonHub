package com.badmintonhub.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
}
