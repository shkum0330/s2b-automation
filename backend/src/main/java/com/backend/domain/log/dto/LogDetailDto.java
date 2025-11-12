package com.backend.domain.log.dto;

import com.backend.domain.log.entity.GenerationLog;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LogDetailDto {
    private Long generationLogId;
    private String memberEmail;
    private LocalDateTime createdAt;
    private boolean success;
    private String errorMessage;
    private String requestBody;  // (Full JSON)
    private String responseBody; // (Full JSON)

    public LogDetailDto(GenerationLog log) {
        this.generationLogId = log.getGenerationLogId();
        this.memberEmail = log.getMember().getEmail();
        this.createdAt = log.getCreatedAt();
        this.success = log.isSuccess();
        this.errorMessage = log.getErrorMessage();
        this.requestBody = log.getRequestBody();
        this.responseBody = log.getResponseBody();
    }
}
