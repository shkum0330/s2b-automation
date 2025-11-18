package com.backend.global.exception;

import org.springframework.http.HttpStatus;

public class InsufficientCreditException extends BaseException {

    private static final String MESSAGE = "크레딧이 부족합니다. (남은 크레딧: %d)";

    public InsufficientCreditException(int remainingCredit) {
        super(HttpStatus.FORBIDDEN, String.format(MESSAGE, remainingCredit));
    }
}