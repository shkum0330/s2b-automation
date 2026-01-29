package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.global.exception.GenerateApiException;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public abstract class AbstractGenerationService implements AiProviderService {

    protected final PromptBuilder promptBuilder;
    protected final ObjectMapper objectMapper;
    protected final WebClient webClient;

    @Override
    public CompletableFuture<GenerateElectronicResponse> fetchMainSpec(String model, String specExample, String productNameExample) {
        String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);
        return fetchFromAi(prompt, GenerateElectronicResponse.class);
    }

    @Override
    public CompletableFuture<CertificationResponse> fetchCertification(String model) {
        String prompt = promptBuilder.buildCertificationPrompt(model);
        return fetchFromAi(prompt, CertificationResponse.class);
    }

    @Override
    public CompletableFuture<GenerateNonElectronicResponse> fetchGeneralSpec(String productName, String specExample) {
        String prompt = promptBuilder.buildGeneralProductSpecPrompt(productName, specExample);
        return fetchFromAi(prompt, GenerateNonElectronicResponse.class);
    }

    private <T> CompletableFuture<T> fetchFromAi(String prompt, Class<T> clazz) {
        HttpEntity<Object> requestEntity = createRequestEntity(prompt);

        return webClient.post()
                .uri(getApiUrl())
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .filter(WebClientResponseException.class::isInstance)
                )
                .map(jsonResponse -> parseResponse(jsonResponse, clazz))
                .toFuture();
    }

    private <T> T parseResponse(String jsonResponse, Class<T> clazz) {
        try {
            String generatedText = extractTextFromResponse(jsonResponse);
            return objectMapper.readValue(generatedText, clazz);
        } catch (Exception e) {
            throw new GenerateApiException("AI 응답 파싱 중 오류", e);
        }
    }

    protected abstract String getApiUrl();
    protected abstract HttpEntity<Object> createRequestEntity(String prompt);
    protected abstract String extractTextFromResponse(String jsonResponse) throws Exception;
}