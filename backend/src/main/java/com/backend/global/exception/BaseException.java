package com.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class BaseException extends RuntimeException {
    private final HttpStatusCode statusCode;

    /**
     * Creates a BaseException carrying an HTTP status code and a detail message.
     *
     * The provided message becomes the exception's detail message (accessible via
     * getMessage()), and the provided statusCode is stored and exposed via
     * getStatusCode().
     *
     * @param statusCode the HTTP status to associate with this exception
     * @param message a short, human-readable description of the error
     */
    public BaseException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
