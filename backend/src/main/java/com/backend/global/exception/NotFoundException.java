package com.backend.global.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BaseException {
    static private final String ENTITY_NOT_FOUND = "찾으시는 %s(이)가 없습니다.";
    /**
     * Creates a NotFoundException with HTTP status 404 (NOT_FOUND) and the given detail message.
     *
     * @param message human-readable detail message describing the missing entity
     */
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /**
     * Creates a NotFoundException for a missing entity.
     *
     * @param entityName display name of the missing entity (e.g., "User"); inserted into the exception message
     * @return a NotFoundException with HTTP 404 status and a standardized "entity not found" message
     */
    public static NotFoundException entityNotFound(String entityName) {
        return new NotFoundException(String.format(ENTITY_NOT_FOUND, entityName));
    }
}
