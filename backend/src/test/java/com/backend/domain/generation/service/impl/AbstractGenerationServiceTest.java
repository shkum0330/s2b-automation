package com.backend.domain.generation.service.impl;

import com.backend.global.exception.GenerateApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractGenerationServiceTest {

    @Test
    @DisplayName("앞에 설명 문구가 있어도 첫 번째 Json 객체를 추출한다")
    void extractFirstJsonObject_withLeadingText_returnsJsonObject() {
        String raw = "thought: search completed\n{\"productName\":\"A\",\"specification\":\"B\"}\nextra text";

        String extracted = AbstractGenerationService.extractFirstJsonObject(raw);

        assertThat(extracted).isEqualTo("{\"productName\":\"A\",\"specification\":\"B\"}");
    }

    @Test
    @DisplayName("코드 펜스가 포함되어도 Json 객체를 추출한다")
    void extractFirstJsonObject_withCodeFence_returnsJsonObject() {
        String raw = "```json\n{\"katsCertificationNumber\":\"\",\"kcCertificationNumber\":\"R-R-TEST\"}\n```";

        String extracted = AbstractGenerationService.extractFirstJsonObject(raw);

        assertThat(extracted).isEqualTo("{\"katsCertificationNumber\":\"\",\"kcCertificationNumber\":\"R-R-TEST\"}");
    }

    @Test
    @DisplayName("문자열 안 중괄호가 있어도 Json 경계를 올바르게 판단한다")
    void extractFirstJsonObject_withBracesInString_returnsCompleteJson() {
        String raw = "prefix {\"specification\":\"정격: {220V}\",\"manufacturer\":\"SAMSUNG\"} suffix";

        String extracted = AbstractGenerationService.extractFirstJsonObject(raw);

        assertThat(extracted).isEqualTo("{\"specification\":\"정격: {220V}\",\"manufacturer\":\"SAMSUNG\"}");
    }

    @Test
    @DisplayName("Json 객체가 없으면 예외를 던진다")
    void extractFirstJsonObject_withoutJson_throwsException() {
        assertThatThrownBy(() -> AbstractGenerationService.extractFirstJsonObject("thought only"))
                .isInstanceOf(GenerateApiException.class)
                .hasMessageContaining("JSON");
    }
}



