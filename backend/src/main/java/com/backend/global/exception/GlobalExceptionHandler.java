package com.backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "검증 오류")
        );
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "message", "요청 검증에 실패했습니다.",
                "errors", errors
        );
    }

    @ExceptionHandler(InsufficientCreditException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResult handleInsufficientCreditException(InsufficientCreditException ex) {
        return new ErrorResult(HttpStatus.FORBIDDEN, ex.getMessage());
    }


    @ExceptionHandler(GenerateApiException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResult handleGenerateApiException(GenerateApiException ex) {
        return new ErrorResult(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResult handleGenericException(Exception ex) {
        log.error("🚨 서버 내부 오류 발생:", ex);
        return new ErrorResult(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에서 오류가 발생했습니다.");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AuthorizationDeniedException e) {
        // 500 대신 403 Forbidden 반환
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("접근 권한이 없습니다.");
    }
}
