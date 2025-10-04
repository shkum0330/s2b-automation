package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.generation.service.AiProviderService; // 인터페이스 변경
import com.backend.global.exception.GenerateApiException;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode; // JsonNode import
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RequiredArgsConstructor
public abstract class AbstractGenerationService implements AiProviderService { // 구현 인터페이스 변경

    protected final PromptBuilder promptBuilder;
    protected final ObjectMapper objectMapper;
    protected final WebClient webClient;

    @Override
    @Retryable(retryFor = {WebClientResponseException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public CompletableFuture<GenerateElectronicResponse> fetchMainSpec(String model, String specExample, String productNameExample) {
        String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);
        return fetchFromAi(prompt, GenerateElectronicResponse.class);
    }

    @Override
    @Retryable(retryFor = {WebClientResponseException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public CompletableFuture<CertificationResponse> fetchCertification(String model) {
        String prompt = promptBuilder.buildCertificationPrompt(model);
        return fetchFromAi(prompt, CertificationResponse.class);
    }

    @Override
    @Retryable(retryFor = {WebClientResponseException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public CompletableFuture<GenerateNonElectronicResponse> fetchGeneralSpec(String productName, String specExample) {
        String prompt = promptBuilder.buildGeneralProductSpecPrompt(productName, specExample);
        return fetchFromAi(prompt, GenerateNonElectronicResponse.class);
    }

    private <T> CompletableFuture<T> fetchFromAi(String prompt, Class<T> clazz) {
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        return webClient.post()
                .uri(getApiUrl())
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, clazz))
                .toFuture();
    }

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
}