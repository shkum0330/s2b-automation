package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateGeneralResponse;
import com.backend.domain.generation.dto.GenerateResponse;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.service.MemberService;
import com.backend.global.exception.GenerateApiException;
import com.backend.domain.generation.service.GenerationService;
import com.backend.global.util.PromptBuilder;
import com.backend.domain.generation.service.ScrapingService;
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
@Slf4j(topic = "GenerationService")
public abstract class AbstractGenerationService implements GenerationService {
    private final MemberService memberService;
    private final ScrapingService scrapingService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final Executor taskExecutor;

    @Override
    public CompletableFuture<GenerateResponse> generateSpec(String model, String specExample, String productNameExample, Member member) {

        // 스크래핑 작업은 별도의 스레드에서 실행
        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findG2bClassificationNumber(model), taskExecutor);
        CompletableFuture<Optional<String>> countryOfOriginFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findCountryOfOrigin(model), taskExecutor);

        CompletableFuture<CertificationResponse> certFuture = this.fetchCertification(model);
        CompletableFuture<GenerateResponse> mainSpecFuture = this.fetchMainSpec(model, specExample, productNameExample);

        // thenCombineAsync를 사용하여 논블로킹 방식으로 비동기 결과들을 조합
        CompletableFuture<GenerateResponse> combinedFuture =  mainSpecFuture
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

        combinedFuture.whenCompleteAsync((result, throwable) -> {
            // throwable이 null이면 모든 작업이 예외 없이 성공했다는 의미
            if (throwable == null) {
                try {
                    memberService.decrementCredit(member.getId());
                    log.info("사용자(email: {})의 크레딧이 성공적으로 차감되었습니다.", member.getEmail());
                } catch (Exception e) {
                    log.error("비동기 작업 성공 후 크레딧 차감 중 에러 발생. 사용자 Email: {}", member.getEmail(), e);
                    // 여기에 추가적인 에러 처리 로직을 넣을 수 있습니다 (예: 운영자에게 알림)
                }
            } else {
                // 비동기 작업 중 하나라도 실패하면 로그만 남기고 크레딧은 차감하지 않음
                log.warn("비동기 작업 실패로 인해 사용자(email: {})의 크레딧을 차감하지 않았습니다. 원인: {}", member.getEmail(), throwable.getMessage());
            }
        }, taskExecutor);

        return combinedFuture; // 원래의 Future를 그대로 반환
    }

    @Override
    public CompletableFuture<GenerateGeneralResponse> generateGeneralSpec(String productName, String specExample, Member member) throws GenerateApiException {
        // 비전자제품용 프롬프트 생성
        String prompt = promptBuilder.buildGeneralProductSpecPrompt(productName, specExample);
        HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
        String apiUrl = getApiUrl();

        // AI API 호출
        CompletableFuture<GenerateGeneralResponse> future = webClient.post()
                .uri(apiUrl)
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonResponse -> parseResponse(jsonResponse, GenerateGeneralResponse.class)) // GenerateGeneralResponse로 파싱
                .toFuture();

        // 비동기 작업 완료 시 크레딧 차감
        future.whenCompleteAsync((result, throwable) -> {
            if (throwable == null) {
                try {
                    memberService.decrementCredit(member.getId());
                    log.info("비전자제품 생성 성공. 사용자(ID: {}) 크레딧 차감.", member.getId());
                } catch (Exception e) {
                    log.error("비전자제품 생성 성공 후 크레딧 차감 중 에러 발생. 사용자 ID: {}", member.getId(), e);
                }
            } else {
                log.warn("비전자제품 생성 실패로 사용자(ID: {}) 크레딧을 차감하지 않았습니다. 원인: {}", member.getId(), throwable.getMessage());
            }
        }, taskExecutor);

        return future;
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