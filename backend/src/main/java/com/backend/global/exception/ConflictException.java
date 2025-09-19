package com.backend.global.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException{
    private static final String EMAIL_ALREADY_IN_USE = "해당 이메일 '%s'은(는) 이미 다른 계정으로 가입되어 있습니다.";

    /**
     * Create a ConflictException representing an HTTP 409 Conflict with a custom message.
     *
     * @param message a human-readable detail describing the conflict (returned to the client)
     */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    /**
     * Creates a ConflictException indicating the given email is already associated with an existing account.
     *
     * @param email the email address that is already in use
     * @return a ConflictException with a localized message describing the email conflict (HTTP 409)
     */
    public static ConflictException emailAlreadyInUse(String email) {
        return new ConflictException(String.format(EMAIL_ALREADY_IN_USE, email));
    }
}
