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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
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
    // 비동기 작업을 위한 Executor 주입
    private final Executor taskExecutor;

    // 템플릿 메서드: 전체 작업 흐름 정의
    @Override
    public GenerateResponse generateSpec(String model, String specExample, String productNameExample) throws GenerateApiException {
        // 1. G2B 번호 스크래핑 (네트워크 I/O)과 AI API 호출 (네트워크 I/O)은 서로 독립적이므로 병렬 처리하여 성능 개선
        CompletableFuture<Optional<String>> g2bNumberFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findG2bClassificationNumber(model),
                taskExecutor
        );

        CompletableFuture<GenerateResponse> aiResponseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // 2. 프롬프트 생성
                String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);

                // 3. API 요청 엔티티 생성 (구현체 위임)
                HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);

                // 4. API URL 가져오기 (구현체 위임)
                String apiUrl = getApiUrl();

                // 5. API 호출
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
                String jsonResponse = response.getBody();

                // 6. 응답에서 텍스트 추출 (구현체 위임)
                String generatedText = extractTextFromResponse(jsonResponse);

                // 7. JSON 파싱
                return objectMapper.readValue(generatedText, GenerateResponse.class);

            } catch (HttpServerErrorException.ServiceUnavailable e) {
                // 비동기 작업 내 예외는 CompletionException으로 감싸서 전파해야 합니다.
                throw new CompletionException(new GenerateApiException(getApiName() + " API가 과부하 상태입니다. 잠시 후 다시 시도해주세요."));
            } catch (JsonProcessingException e) {
                throw new CompletionException(new GenerateApiException("AI가 생성한 응답의 형식이 잘못되었습니다."));
            } catch (Exception e) {
                // extractTextFromResponse에서 발생할 수 있는 예외 처리
                throw new CompletionException(new GenerateApiException("AI 응답 처리 중 오류가 발생했습니다.", e));
            }
        }, taskExecutor);

        // 8. 두 비동기 작업의 결과를 조합
        try {
            return g2bNumberFuture.thenCombine(aiResponseFuture, (g2bNumberOpt, aiResponse) -> {
                g2bNumberOpt.ifPresent(aiResponse::setG2bClassificationNumber);
                return aiResponse;
            }).join(); // join()은 모든 작업이 완료될 때까지 기다리고, 예외 발생 시 CompletionException을 던집니다.
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GenerateApiException) {
                throw (GenerateApiException) cause;
            }
            throw new GenerateApiException("사양 생성 중 알 수 없는 오류가 발생했습니다.", e);
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
