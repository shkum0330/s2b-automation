package com.backend.domain.log.entity;

import com.backend.domain.member.entity.Member;
import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "generation_log", indexes = {
        @Index(name = "idx_log_model_name", columnList = "model_name"), // 모델명 검색 인덱스
        @Index(name = "idx_log_success_date", columnList = "success, createdAt") // 실패 로그 검색 인덱스
})
public class GenerationLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_log_id")
    private Long generationLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestBody; // 요청 DTO의 JSON 문자열

    @Column(columnDefinition = "TEXT")
    private String responseBody; // 응답 DTO의 JSON 문자열

    @Column(nullable = false)
    private boolean success; // 성공 여부

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // 실패 시 에러 메시지

    // 검색 성능 향상을 위한 모델명 컬럼
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Builder
    public GenerationLog(Member member, String requestBody, String responseBody,
                         boolean success, String errorMessage, String modelName) {
        this.member = member;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.success = success;
        this.errorMessage = errorMessage;
        this.modelName = modelName;
    }
}