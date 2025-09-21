package com.backend.service;

import com.backend.domain.generation.dto.GenerateResponse;
import com.backend.domain.generation.service.impl.GeminiService;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.domain.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("dev")
@SpringBootTest
@Slf4j
@Transactional // 테스트 후 데이터베이스 롤백을 위해 추가
class GeminiServiceTest {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .email("testuser@example.com")
                .name("테스트유저")
                .provider("TEST")
                .providerId("test-provider-id")
                .role(Role.PAID_USER) // 유료 사용자로 설정
                .build();
        testMember.setCredit(10);
    }


    @Test
    @DisplayName("서비스 입출력 로깅 테스트")
    void ioTest() {

        log.info("--- 1. 테스트 입력값 설정 ---");
        String model = "AX033B310GBD";
        String specExample = "10평형(33㎡) / 집진(헤파)필터 / 초미세먼지제거 / 탈취 / 제균";
        String productNameExample = "삼성전자 블루스카이 3100 공기청정기";
        log.info("Model: {}", model);
        log.info("Spec Example: {}", specExample);
        log.info("Product Name Example: {}", productNameExample);

        log.info("\n--- 2. 서비스 메소드 실행 ---");
        try {
            CompletableFuture<GenerateResponse> future = geminiService.generateSpec(model, specExample, productNameExample);

            log.info("AI와 스크래핑 작업이 완료될 때까지 대기합니다...");
            GenerateResponse response = future.get();

            log.info("\n--- 3. 받아온 모든 특성들 로그 출력 (줄 단위 구분) ---");
            if (response != null) {
                log.info("productName: {}", response.getProductName());
                log.info("specification: {}", response.getSpecification());
                log.info("modelName: {}", response.getModelName());
                log.info("manufacturer: {}", response.getManufacturer());
                log.info("countryOfOrigin: {}", response.getCountryOfOrigin());
                log.info("g2bClassificationNumber: {}", response.getG2bClassificationNumber());
                log.info("katsCertificationNumber: {}", response.getKatsCertificationNumber());
                log.info("kcCertificationNumber: {}", response.getKcCertificationNumber());
            } else {
                log.warn("응답 객체가 null입니다.");
            }

        } catch (Exception e) {
            log.error("테스트 실행 중 예외 발생", e);
        }
    }

    // --- [NEW] 크레딧 차감 성공 테스트 메서드 ---
    @Test
    @DisplayName("크레딧 차감 성공 테스트")
    void decrementCredit_success() {
        // given
        memberRepository.save(testMember);

        // when
        memberService.decrementCredit(testMember);

        // then
        Member foundMember = memberRepository.findByEmail("testuser@example.com").orElseThrow();
        assertThat(foundMember.getCredit()).isEqualTo(9);
    }


    // --- [NEW] 크레딧 부족 시 실패 테스트 메서드 ---
    @Test
    @DisplayName("크레딧 부족 시 예외 발생 테스트")
    void decrementCredit_fail_when_credit_is_insufficient() {
        // given
        // testMember의 credit은 기본값 0
        testMember.setCredit(0);
        memberRepository.save(testMember);

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            memberService.decrementCredit(testMember);
        });

        assertThat(exception.getMessage()).isEqualTo("크레딧이 부족합니다.");
        log.info("크레딧이 부족합니다.");
    }
}