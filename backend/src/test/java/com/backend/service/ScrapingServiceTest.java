package com.backend.service;

import com.backend.domain.generation.service.ScrapingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class ScrapingServiceTest {

    @Autowired
    private ScrapingService scrapingService;

    @Test
    @DisplayName("나라장터 물품분류번호 조회가 정상적으로 동작한다")
    void findG2bClassificationNumber_success() {
        // given
        String modelName = "AX033B310GBD";
        String expectedNumber = "24574852";

        // when
        Optional<String> actualNumberOpt = scrapingService.findG2bClassificationNumber(modelName);

        // then
        assertTrue(actualNumberOpt.isPresent(), "결과값이 존재해야 합니다.");
        assertEquals(expectedNumber, actualNumberOpt.get(), "실제 스크래핑 결과가 예상 값과 일치해야 합니다.");
    }

    @Test
    @DisplayName("존재하지 않는 모델명은 나라장터 물품분류번호를 비어 있는 값으로 반환한다")
    void findG2bClassificationNumber_whenNotFound() {
        // given
        String modelName = "THIS_MODEL_NAME_DOES_NOT_EXIST_XYZ123";

        // when
        Optional<String> actualNumberOpt = scrapingService.findG2bClassificationNumber(modelName);

        // then
        assertFalse(actualNumberOpt.isPresent(), "존재하지 않는 모델명 검색 시 결과가 비어있어야 합니다.");
    }

    @Test
    @DisplayName("원산지 조회가 정상적으로 동작한다")
    void findCountryOfOrigin_success() {
        // given
        String modelName = "AX033B310GBD";
        String expectedCountryName = "태국";

        // when
        Optional<String> actualCountryCodeOpt = scrapingService.findCountryOfOrigin(modelName);

        log.info("스크래핑한 국가코드: {}",actualCountryCodeOpt.get());
        // then
        assertTrue(actualCountryCodeOpt.isPresent(), "국가코드 결과값이 존재해야 합니다.");
        assertEquals(expectedCountryName, actualCountryCodeOpt.get(), "스크래핑된 국가코드가 예상 값과 일치해야 합니다.");
    }

    @Test
    @DisplayName("존재하지 않는 모델명은 원산지를 비어 있는 값으로 반환한다")
    void findCountryOfOrigin_whenNotFound() {
        // given
        String modelName = "THIS_MODEL_NAME_DOES_NOT_EXIST_XYZ123";

        // when
        Optional<String> actualCountryCodeOpt = scrapingService.findCountryOfOrigin(modelName);
        // then
        assertFalse(actualCountryCodeOpt.isPresent(), "존재하지 않는 모델명 검색 시 국가코드 결과가 비어있어야 합니다.");
    }
}



