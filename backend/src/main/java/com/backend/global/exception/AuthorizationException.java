package com.backend.global.exception;

import org.springframework.http.HttpStatus;

public class AuthorizationException extends BaseException {

    /**
     * Creates an AuthorizationException representing an HTTP 403 Forbidden error.
     *
     * @param message human-readable detail about the authorization failure
     */
    public AuthorizationException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

}
