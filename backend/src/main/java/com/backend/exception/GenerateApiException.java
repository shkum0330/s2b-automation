package com.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class GenerateApiException extends RuntimeException {
    public GenerateApiException(String message) {
        super(message);
    }
}
