package com.backend.domain.log.listener;

import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.log.entity.GenerationLog;
import com.backend.domain.log.event.GenerationLogEvent;
import com.backend.domain.log.repository.GenerationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationLogListener {

    private final GenerationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    /**
     * GenerationLogEvent가 발행되면, loggingTaskExecutor 스레드 풀을 사용하여
     * 메인 작업과 완전히 분리되어 비동기로 이 메서드를 실행함
     */
    @Async("loggingTaskExecutor")
    @EventListener
    @Transactional
    public void handleGenerationLog(GenerationLogEvent event) {
        try {
            // DTO 객체를 JSON 문자열로 변환
            String requestBody = convertToJson(event.getRequestDto());
            String responseBody = (event.getError() == null) ? convertToJson(event.getResponseDto()) : null;
            String errorMessage = (event.getError() != null) ? getRootCauseMessage(event.getError()) : null;

            String modelName = null;
            if (event.getRequestDto() instanceof GenerateElectronicRequest) {
                modelName = ((GenerateElectronicRequest) event.getRequestDto()).getModelName();
            }

            // 로그 엔티티를 빌드
            GenerationLog logEntry = GenerationLog.builder()
                    .member(event.getMember())
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .success(event.getError() == null)
                    .errorMessage(errorMessage)
                    .modelName(modelName)
                    .build();

            // DB에 저장
            logRepository.save(logEntry);

        } catch (Exception e) {
            // 로깅에 실패하더라도 메인 기능에 영향을 주지 않도록 예외 처리
            log.error("GenerationLog 저장 실패. Member ID: {}",
                    event.getMember() != null ? event.getMember().getId() : "null", e);
        }
    }

    private String convertToJson(Object object) {
        try {
            if (object == null) return null;
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("로깅 JSON 직렬화 실패: {}", e.getMessage());
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }

    // CompletableFuture에서 발생한 실제 원인 예외를 찾는다
    private String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) return null;
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage();
    }
}