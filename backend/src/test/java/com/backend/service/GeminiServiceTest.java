package com.backend.service;

import com.backend.generation.dto.GenerateResponse;
import com.backend.generation.service.impl.GeminiService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

@SpringBootTest
@Slf4j
class GeminiServiceTest {

    @Autowired
    private GeminiService geminiService;

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
            // 1. 서비스는 이제 CompletableFuture를 반환합니다.
            CompletableFuture<GenerateResponse> future = geminiService.generateSpec(model, specExample, productNameExample);

            // 2. .get()을 호출하여 비동기 작업이 완료되기를 기다립니다.
            log.info("AI와 스크래핑 작업이 완료될 때까지 대기합니다...");
            GenerateResponse response = future.get(); // 여기서 블로킹(대기) 발생

            log.info("\n--- 3. 받아온 모든 특성들 로그 출력 (줄 단위 구분) ---");
            if (response != null) {
                // 각 필드를 개별적으로 로그에 출력
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
}