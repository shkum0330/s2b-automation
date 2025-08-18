package com.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScrapingServiceTest {

    @Autowired
    private ScrapingService scrapingService;

    @Test
    @DisplayName("통합 테스트 성공: 실제 스크래핑으로 물품목록번호를 가져온다")
    void givenModelName_whenScrapingRealWebsite_thenReturnsCorrectNumber() {
        // given
        String modelName = "AX033B310GBD";
        String expectedNumber = "24574852";

        // when
        String actualNumber = scrapingService.findG2bClassificationNumber(modelName);

        // then
        assertNotNull(actualNumber, "결과 값은 null이 아니어야 합니다.");
        assertEquals(expectedNumber, actualNumber, "실제 스크래핑 결과가 예상 값과 일치해야 합니다.");
    }

    @Test
    @DisplayName("통합 테스트 실패: 존재하지 않는 모델명으로 검색 시 빈 문자열을 반환한다")
    void givenNonExistentModelName_whenScraping_thenReturnsEmptyString() {
        // given
        String modelName = "THIS_MODEL_NAME_DOES_NOT_EXIST_XYZ123";

        // when
        String actualNumber = scrapingService.findG2bClassificationNumber(modelName);

        // then
        assertNotNull(actualNumber, "결과 값은 null이 아니어야 합니다.");
        assertEquals("", actualNumber, "존재하지 않는 모델명 검색 시 빈 문자열을 반환해야 합니다.");
    }
}