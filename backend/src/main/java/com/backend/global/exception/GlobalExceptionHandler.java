package com.backend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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


    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ErrorResult handleAsyncExceptions(Exception ex) {
        Throwable cause = ex.getCause(); // 진짜 원인 예외 꺼내기

        if (cause instanceof InsufficientCreditException) {
            return new ErrorResult(HttpStatus.FORBIDDEN, cause.getMessage());
        }
        if (cause instanceof GenerateApiException) {
            return new ErrorResult(HttpStatus.SERVICE_UNAVAILABLE, cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            return new ErrorResult(HttpStatus.BAD_REQUEST, cause.getMessage());
        }

        // 그 외 알 수 없는 에러인 경우
        return new ErrorResult(HttpStatus.INTERNAL_SERVER_ERROR, "비동기 작업 중 오류: " + (cause != null ? cause.getMessage() : ex.getMessage()));
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
        ex.printStackTrace();
        return new ErrorResult(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에서 오류가 발생했습니다: " + ex.getMessage());
    }
}
