package com.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class GeminiApiException extends RuntimeException {
    public GeminiApiException(String message) {
        super(message);
    }
}
