package com.backend.domain.log.dto;

import com.backend.domain.log.entity.GenerationLog;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LogSummaryDto {
    private Long generationLogId;
    private String memberEmail;
    private LocalDateTime createdAt;
    private boolean success;
    private String modelName;
    private String errorMessage;

    public LogSummaryDto(GenerationLog log) {
        this.generationLogId = log.getGenerationLogId();
        this.memberEmail = log.getMember().getEmail();
        this.createdAt = log.getCreatedAt();
        this.success = log.isSuccess();
        this.modelName = log.getModelName();
        this.errorMessage = log.getErrorMessage() != null ?
                (log.getErrorMessage().length() > 50 ? log.getErrorMessage().substring(0, 50) + "..." : log.getErrorMessage())
                : null;
    }
}
