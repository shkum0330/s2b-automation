package com.backend.domain.log.service;

import com.backend.domain.log.dto.LogDetailDto;
import com.backend.domain.log.dto.LogSearchRequest;
import com.backend.domain.log.dto.LogSummaryDto;
import com.backend.domain.log.entity.GenerationLog;
import com.backend.domain.log.repository.GenerationLogRepository;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("dev")
class LogServiceTest {

    @Autowired
    private LogService logService;

    @Autowired
    private GenerationLogRepository logRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member memberA;
    private Member memberB;
    private String testEmailPrefix;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        testEmailPrefix = "logtest-" + suffix;

        memberA = memberRepository.save(Member.builder()
                .email(testEmailPrefix + "-userA@example.com")
                .name("User A")
                .provider("test")
                .providerId("provider-" + suffix + "-1")
                .role(Role.FREE_USER)
                .build());

        memberB = memberRepository.save(Member.builder()
                .email(testEmailPrefix + "-userB@example.com")
                .name("User B")
                .provider("test")
                .providerId("provider-" + suffix + "-2")
                .role(Role.FREE_USER)
                .build());

        logRepository.save(GenerationLog.builder()
                .member(memberA)
                .success(true)
                .requestBody("reqA_success")
                .responseBody("resA_success")
                .modelName("MODEL_A")
                .build());

        logRepository.save(GenerationLog.builder()
                .member(memberA)
                .success(false)
                .requestBody("reqA_fail")
                .errorMessage("Error")
                .modelName("MODEL_A_FAIL")
                .build());

        logRepository.save(GenerationLog.builder()
                .member(memberB)
                .success(true)
                .requestBody("reqB_success")
                .responseBody("resB_success")
                .modelName("MODEL_B")
                .build());
    }

    @Test
    @DisplayName("로그 검색 - 이메일로 필터링")
    void searchLogs_filterByEmail() {
        LogSearchRequest request = new LogSearchRequest();
        request.setMemberEmail(memberA.getEmail());

        Pageable pageable = PageRequest.of(0, 10);
        Page<LogSummaryDto> result = logService.searchLogs(request, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(log -> log.getMemberEmail().equals(memberA.getEmail()));
    }

    @Test
    @DisplayName("로그 검색 - 성공 여부로 필터링")
    void searchLogs_filterBySuccess() {
        LogSearchRequest request = new LogSearchRequest();
        request.setMemberEmail(testEmailPrefix);
        request.setSuccess(true);

        Pageable pageable = PageRequest.of(0, 10);
        Page<LogSummaryDto> result = logService.searchLogs(request, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(LogSummaryDto::isSuccess);
    }

    @Test
    @DisplayName("로그 검색 - 복합 조건(이메일 + 실패) 필터링")
    void searchLogs_filterByEmailAndSuccess() {
        LogSearchRequest request = new LogSearchRequest();
        request.setMemberEmail(memberA.getEmail());
        request.setSuccess(false);

        Pageable pageable = PageRequest.of(0, 10);
        Page<LogSummaryDto> result = logService.searchLogs(request, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getMemberEmail()).isEqualTo(memberA.getEmail());
        assertThat(result.getContent().get(0).isSuccess()).isFalse();
    }

    @Test
    @DisplayName("로그 상세 조회 - 성공")
    void getLogDetail_success() {
        GenerationLog savedLog = logRepository.save(GenerationLog.builder()
                .member(memberA)
                .success(true)
                .requestBody("detail_req")
                .responseBody("detail_res")
                .build());

        LogDetailDto result = logService.getLogDetail(savedLog.getGenerationLogId());

        assertThat(result).isNotNull();
        assertThat(result.getGenerationLogId()).isEqualTo(savedLog.getGenerationLogId());
        assertThat(result.getRequestBody()).isEqualTo("detail_req");
        assertThat(result.getResponseBody()).isEqualTo("detail_res");
    }

    @Test
    @DisplayName("로그 상세 조회 - 존재하지 않는 ID 예외 발생")
    void getLogDetail_notFound() {
        Long nonExistentId = 99999L;

        assertThrows(NotFoundException.class, () -> {
            logService.getLogDetail(nonExistentId);
        });
    }
}