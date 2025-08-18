package com.backend.service;

import com.backend.dto.GenerateResponse;
import com.backend.dto.PriceInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
            GenerateResponse response = geminiService.generateSpec(model, specExample, productNameExample);

            log.info("\n--- 3. 받아온 모든 특성들 로그 출력 (줄 단위 구분) ---");
            if (response != null) {
                // 각 필드를 개별적으로 로그에 출력
                log.info("productName: {}", response.getProductName());
                log.info("specification: {}", response.getSpecification());
                log.info("modelName: {}", response.getModelName());
                log.info("국가기술표준원 인증번호: {}", response.getKatsCertificationNumber());
                log.info("KC인증번호: {}", response.getKcCertificationNumber());
                log.info("제조사: {}", response.getManufacturer());
                log.info("원산지: {}", response.getCountryOfOrigin());
                log.info("G2B 물품목록번호: {}", response.getG2bClassificationNumber());
                // 가격 정보 리스트를 하나씩 순회하며 출력
                if (response.getPriceList() != null && !response.getPriceList().isEmpty()) {
                    log.info("priceList:");
                    int count = 1;
                    for (PriceInfo price : response.getPriceList()) {
                        log.info("  {}. storeName: {}", count, price.getStoreName());
                        log.info("     price: {}", price.getPrice());
                        log.info("     storeLink: {}", price.getStoreLink());
                        count++;
                    }
                } else {
                    log.info("priceList: (정보 없음)");
                }
            } else {
                log.warn("응답 객체가 null입니다.");
            }

        } catch (Exception e) {
            log.error("테스트 실행 중 예외 발생", e);
        }
    }
}