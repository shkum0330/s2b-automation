package com.backend.service.impl;

import com.backend.dto.CertificationResponse;
import com.backend.dto.GenerateResponse;
import com.backend.exception.GenerateApiException;
import com.backend.service.GenerationService;
import com.backend.service.PromptBuilder;
import com.backend.service.ScrapingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

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

        CompletableFuture<CertificationResponse> certFuture = this.fetchCertification(model);
        CompletableFuture<GenerateResponse> mainSpecFuture = this.fetchMainSpec(model, specExample, productNameExample);

        // 세 개의 비동기 작업이 모두 완료되면 그 결과를 조합하여 최종 GenerateResponse 생성
        return CompletableFuture.allOf(g2bFuture, certFuture, mainSpecFuture)
                .thenApplyAsync(v -> {
                    try {
                        GenerateResponse finalResponse = mainSpecFuture.get();
                        CertificationResponse certResponse = certFuture.get();
                        Optional<String> g2bOpt = g2bFuture.get();

                        finalResponse.setCertificationNumber(certResponse);
                        g2bOpt.ifPresent(finalResponse::setG2bClassificationNumber);

                        return finalResponse;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new CompletionException(e);
                    }
                }, taskExecutor);
    }

    private CompletableFuture<CertificationResponse> fetchCertification(String model) {
        String prompt = promptBuilder.buildCertificationPrompt(model);
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        String apiUrl = getApiUrl();

        return webClient.post()
                .uri(apiUrl)
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, CertificationResponse.class))
                .toFuture();
    }

    private CompletableFuture<GenerateResponse> fetchMainSpec(String model, String specExample, String productNameExample) {
        String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        String apiUrl = getApiUrl();

        return webClient.post()
                .uri(apiUrl)
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, GenerateResponse.class))
                .toFuture();
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