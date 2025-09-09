package com.backend.service.impl;

import com.backend.dto.CertificationResponse;
import com.backend.dto.GenerateResponse;
import com.backend.exception.GenerateApiException;
import com.backend.service.GenerationService;
import com.backend.service.util.PromptBuilder;
import com.backend.service.ScrapingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractGenerationService implements GenerationService {
    private final ScrapingService scrapingService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final Executor taskExecutor;

    @Override
    public CompletableFuture<GenerateResponse> generateSpec(String model, String specExample, String productNameExample) {

        // 스크래핑 작업은 별도의 스레드에서 실행
        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findG2bClassificationNumber(model), taskExecutor);
        CompletableFuture<Optional<String>> countryOfOriginFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findCountryOfOrigin(model), taskExecutor);

        CompletableFuture<CertificationResponse> certFuture = this.fetchCertification(model);
        CompletableFuture<GenerateResponse> mainSpecFuture = this.fetchMainSpec(model, specExample, productNameExample);

        // thenCombineAsync를 사용하여 논블로킹 방식으로 비동기 결과들을 조합
        return mainSpecFuture
                .thenCombineAsync(certFuture, (mainSpec, cert) -> {
                    mainSpec.setCertificationNumber(cert);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(g2bFuture, (mainSpec, g2bOpt) -> {
                    g2bOpt.ifPresent(mainSpec::setG2bClassificationNumber);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(countryOfOriginFuture, (mainSpec, countryOpt) -> {
                    countryOpt.ifPresent(mainSpec::setCountryOfOrigin); // Scraping 결과가 있으면 Gemini에서 받아온 결과를 덮어씀
                    return mainSpec;
                }, taskExecutor);
    }

    @Retryable(
            retryFor = {
                    WebClientResponseException.InternalServerError.class,
                    WebClientResponseException.ServiceUnavailable.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    protected CompletableFuture<CertificationResponse> fetchCertification(String model) {
        String prompt = promptBuilder.buildCertificationPrompt(model);
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        String apiUrl = getApiUrl();

//        logRequestAsJson(apiUrl, requestEntity.getBody());

        return webClient.post()
                .uri(apiUrl)
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, CertificationResponse.class))
                .toFuture();
    }

    @Retryable(
            retryFor = {
                    WebClientResponseException.InternalServerError.class,
                    WebClientResponseException.ServiceUnavailable.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    protected CompletableFuture<GenerateResponse> fetchMainSpec(String model, String specExample, String productNameExample) {
        String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        String apiUrl = getApiUrl();

//        logRequestAsJson(apiUrl, requestEntity.getBody());

        return webClient.post()
                .uri(apiUrl)
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, GenerateResponse.class))
                .toFuture();
    }

    // 로깅을 위한 메서드
    private void logRequestAsJson(String url, Object body) {
        try {
            String jsonBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            log.info("\n--- Sending API Request ---\nURL: {}\nBody:\n{}", url, jsonBody);
        } catch (JsonProcessingException e) {
            log.warn("request body 직렬화 실패", e);
        }
    }

    // 제네릭을 사용하여 공통 파싱 로직 추출
    private <T> T parseResponse(String jsonResponse, Class<T> clazz) {
        try {
            String generatedText = extractTextFromResponse(jsonResponse);
            return objectMapper.readValue(generatedText, clazz);
        } catch (Exception e) {
            throw new CompletionException(new GenerateApiException("AI 응답 파싱 중 오류", e));
        }
    }

    protected abstract String getApiUrl();
    protected abstract HttpEntity<Map<String, Object>> createRequestEntity(String prompt);
    protected abstract String extractTextFromResponse(String jsonResponse) throws Exception;
    protected abstract String getApiName();
}