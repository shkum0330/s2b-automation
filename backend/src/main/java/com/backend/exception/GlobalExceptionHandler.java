package com.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<Map<String, String>> handleGeminiApiException(GeminiApiException ex) {
        // 사용자에게 보여줄 에러 메시지를 JSON 형태로 구성합니다.
        Map<String, String> errorResponse = Map.of("error", ex.getMessage());
        // 503 Service Unavailable 상태 코드와 함께 응답을 반환합니다.
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 다른 모든 예외를 처리하고 싶다면 아래와 같이 추가할 수 있습니다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> errorResponse = Map.of("error", "서버 내부에서 알 수 없는 오류가 발생했습니다.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
