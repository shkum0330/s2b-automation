package com.backend.domain.log;

import com.backend.domain.member.entity.Member;
import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "generation_log") // 로그 테이블
public class GenerationLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_log_id") // 기본 키
    private Long generationLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false) // 요청한 회원
    private Member member;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestBody; // 요청 DTO의 JSON 문자열

    @Column(columnDefinition = "TEXT")
    private String responseBody; // 응답 DTO의 JSON 문자열

    @Column(nullable = false)
    private boolean success; // 성공 여부

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // 실패 시 에러 메시지

    @Builder
    public GenerationLog(Member member, String requestBody, String responseBody, boolean success, String errorMessage) {
        this.member = member;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.success = success;
        this.errorMessage = errorMessage;
    }
}