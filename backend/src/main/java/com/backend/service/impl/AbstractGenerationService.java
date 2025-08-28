package com.backend.service.impl;

// CertificationResponse import 제거
import com.backend.dto.GenerateResponse;
import com.backend.exception.GenerateApiException;
import com.backend.service.GenerationService;
import com.backend.service.PromptBuilder;
import com.backend.service.ScrapingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractGenerationService implements GenerationService {
    private final ScrapingService scrapingService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Executor taskExecutor;

    @Override
    public GenerateResponse generateSpec(String model, String specExample, String productNameExample) throws GenerateApiException {
        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(
                () -> scrapingService.findG2bClassificationNumber(model), taskExecutor);

        // certFuture의 반환 타입을 GenerateResponse로 변경
        CompletableFuture<GenerateResponse> certFuture = CompletableFuture.supplyAsync(
                () -> fetchCertification(model), taskExecutor);

        CompletableFuture<GenerateResponse> mainSpecFuture = CompletableFuture.supplyAsync(
                () -> fetchMainSpec(model, specExample, productNameExample), taskExecutor);

        try {
            CompletableFuture.allOf(g2bFuture, certFuture, mainSpecFuture).join();

            GenerateResponse finalResponse = mainSpecFuture.get();
            GenerateResponse certResponse = certFuture.get(); // 이제 GenerateResponse 타입
            Optional<String> g2bOpt = g2bFuture.get();

            // certResponse에서 얻은 인증번호 정보를 finalResponse에 병합
            finalResponse.setKatsCertificationNumber(certResponse.getKatsCertificationNumber());
            finalResponse.setKcCertificationNumber(certResponse.getKcCertificationNumber());
            g2bOpt.ifPresent(finalResponse::setG2bClassificationNumber);

            return finalResponse;

        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GenerateApiException) {
                throw (GenerateApiException) cause;
            }
            throw new GenerateApiException("사양 생성 중 알 수 없는 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new GenerateApiException("비동기 작업 결과 조합 중 오류가 발생했습니다.", e);
        }
    }

    private GenerateResponse fetchCertification(String model) {
        try {
            String prompt = promptBuilder.buildCertificationPrompt(model);
            log.info(prompt);
            HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
            String apiUrl = getApiUrl();
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
            String generatedText = extractTextFromResponse(response.getBody());
//            log.info("{}의 생성된 인증번호 대답: {}", model, generatedText);
            // objectMapper가 JSON을 GenerateResponse 객체로 변환하도록 수정
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (Exception e) {
            log.warn("인증번호 조회 중 오류 발생 (모델: {}): {}", model, e.getMessage());
            return new GenerateResponse(); // 실패 시 빈 GenerateResponse 객체 반환
        }
    }

    private GenerateResponse fetchMainSpec(String model, String specExample, String productNameExample) {
        try {
            String prompt = promptBuilder.buildProductSpecPrompt(model, specExample, productNameExample);
            HttpEntity<Map<String, Object>> requestEntity = createRequestEntity(prompt);
            String apiUrl = getApiUrl();
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
            String generatedText = extractTextFromResponse(response.getBody());
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            throw new CompletionException(new GenerateApiException(getApiName() + " API가 과부하 상태입니다."));
        } catch (JsonProcessingException e) {
            throw new CompletionException(new GenerateApiException("AI가 생성한 응답의 형식이 잘못되었습니다."));
        } catch (Exception e) {
            throw new CompletionException(new GenerateApiException("AI 응답 처리 중 오류가 발생했습니다.", e));
        }
    }

    protected abstract String getApiUrl();
    protected abstract HttpEntity<Map<String, Object>> createRequestEntity(String prompt);
    protected abstract String extractTextFromResponse(String jsonResponse) throws Exception;
    protected abstract String getApiName();
}