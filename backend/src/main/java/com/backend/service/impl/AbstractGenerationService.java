package com.backend.service.impl;

import com.backend.dto.GenerateResponse;
import com.backend.exception.GenerateApiException;
import com.backend.service.GenerationService;
import com.backend.service.PromptBuilder;
import com.backend.service.ScrapingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// 템플릿 메서드 패턴
// 생성자 주입을 원하는 공통 빈들을 여기에 모은다.
@RequiredArgsConstructor
public abstract class AbstractGenerationService implements GenerationService {
    // 공통으로 사용되는 빈
    private final ScrapingService scrapingService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    // 템플릿 메서드: 전체 작업 흐름 정의
    @Override
    public GenerateResponse generateSpec(String model, String specExample, String productNameExample) throws Exception {
        // 1. (공통) G2B 번호 스크래핑
        String g2bNumber = scrapingService.findG2bClassificationNumber(model);

        // 2. (공통) 프롬프트 생성
        String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);

        try {
            // 3. (구현체에 위임) API 요청 엔티티 생성
            HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);

            // 4. (구현체에 위임) API URL 가져오기
            String apiUrl = getApiUrl();

            // 5. (공통) API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
            String jsonResponse = response.getBody();

            // 6. (구현체에 위임) 응답에서 텍스트 추출
            String generatedText = extractTextFromResponse(jsonResponse);

            // 7. (공통) JSON 파싱
            GenerateResponse finalResponse = objectMapper.readValue(generatedText, GenerateResponse.class);

            // 8. (공통) 스크래핑 결과와 AI 생성 결과 결합
            finalResponse.setG2bClassificationNumber(g2bNumber);
            return finalResponse;

        } catch (HttpServerErrorException.ServiceUnavailable e) {
            throw new GenerateApiException(getApiName() + " API가 과부하 상태입니다. 잠시 후 다시 시도해주세요.");
        } catch (JsonProcessingException e) {
            throw new GenerateApiException("AI가 생성한 응답의 형식이 잘못되었습니다.");
        }
    }

    // AI 서비스의 API URL 반환
    protected abstract String getApiUrl();

    // AI 서비스의 API 요청에 맞는 HttpEntity 생성
    protected abstract HttpEntity<Map<String, Object>> createRequestEntity(String prompt);

     // AI 서비스의 응답 JSON 구조에 맞게 텍스트 추출
    protected abstract String extractTextFromResponse(String jsonResponse) throws Exception;

    // 예외 메시지에 사용할 API 이름 반환
    protected abstract String getApiName();
}
