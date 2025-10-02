package com.backend.service;

import com.backend.domain.generation.dto.GenerateElectronicResponse;
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
    @DisplayName("전자제품 서비스 입출력 로깅 테스트")
    void electronicsIoTest() {

        log.info("--- 1. 테스트 입력값 설정 ---");
        String model = "AX033B310GBD";
        String specExample = "10평형(33㎡) / 집진(헤파)필터 / 초미세먼지제거 / 탈취 / 제균";
        String productNameExample = "삼성전자 블루스카이 3100 공기청정기";
        log.info("Model: {}", model);
        log.info("Spec Example: {}", specExample);
        log.info("Product Name Example: {}", productNameExample);

        log.info("\n--- 2. 서비스 메소드 실행 ---");
        try {
            CompletableFuture<GenerateElectronicResponse> future = geminiService.generateSpec(model, specExample, productNameExample, testMember);

            log.info("AI와 스크래핑 작업이 완료될 때까지 대기합니다...");
            GenerateElectronicResponse response = future.get();

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

    @Test
    @DisplayName("비전자제품 서비스 입출력 로깅 테스트")
    void generalIoTest() {
        // given
        log.info("--- 1. 비전자제품 테스트 입력값 설정 ---");
        String productName = "앙블랑 세이프 버건디 물티슈 캡형";
        String specExample = "{용량} / 엠보싱타입 / 평량 80gsm";
        log.info("Product Name: {}", productName);
        log.info("Spec Example: {}", specExample);

        // when
        log.info("\n--- 2. 비전자제품 서비스 메소드 실행 ---");
        try {
            // geminiService의 새로운 메서드 호출
            CompletableFuture<com.backend.domain.generation.dto.GenerateGeneralResponse> future = geminiService.generateGeneralSpec(productName, specExample, testMember);

            log.info("AI 작업이 완료될 때까지 대기합니다...");
            com.backend.domain.generation.dto.GenerateGeneralResponse response = future.get(); // 비동기 작업 완료까지 대기

            // then
            log.info("\n--- 3. 받아온 모든 특성들 로그 출력 (줄 단위 구분) ---");
            if (response != null) {
                log.info("productName: {}", response.getProductName());
                log.info("specification: {}", response.getSpecification());
                log.info("manufacturer: {}", response.getManufacturer());
                log.info("countryOfOrigin: {}", response.getCountryOfOrigin());
            } else {
                log.warn("응답 객체가 null입니다.");
            }

        } catch (Exception e) {
            log.error("비전자제품 테스트 실행 중 예외 발생", e);
        }
    }
}