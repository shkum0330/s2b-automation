package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.global.exception.GenerateApiException;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractGenerationService implements AiProviderService {

    protected final PromptBuilder promptBuilder;
    protected final ObjectMapper objectMapper;
    protected final WebClient webClient;
    // 운영 환경별로 timeout/retry 튜닝이 가능하도록 프로퍼티로 분리
    @Value("${generation.ai.request-timeout:20s}")
    private Duration requestTimeout = Duration.ofSeconds(20);
    @Value("${generation.ai.retry.max-attempts:1}")
    private long retryMaxAttempts = 1;
    @Value("${generation.ai.retry.backoff:1s}")
    private Duration retryBackoff = Duration.ofSeconds(1);

    @Override
    public CompletableFuture<GenerateElectronicResponse> fetchMainSpec(
            String model,
            String specExample,
            String productNameExample
    ) {
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
        final long requestStartNanos = System.nanoTime();
        HttpEntity<Object> requestEntity = createRequestEntity(prompt);

        Mono<String> responseMono = webClient.post()
                .uri(getApiUrl())
                .headers(headers -> headers.addAll(requestEntity.getHeaders()))
                .bodyValue(requestEntity.getBody())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(requestTimeout);

        // 4xx 전체 재시도는 오히려 지연만 늘릴 수 있어, 재시도 가치가 있는 오류만 선별
        if (retryMaxAttempts > 0) {
            responseMono = responseMono.retryWhen(
                    Retry.backoff(retryMaxAttempts, retryBackoff)
                            .filter(this::isRetryableError)
                            .doBeforeRetry(retrySignal -> log.warn(
                                    "AI 호출 재시도: responseType={}, retry={}, elapsedMs={}, error={}",
                                    clazz.getSimpleName(),
                                    retrySignal.totalRetries() + 1,
                                    elapsedMillis(requestStartNanos),
                                    rootMessage(retrySignal.failure())
                            ))
            );
        }

        return responseMono.map(jsonResponse -> {
                    log.info(
                            "AI 응답 수신: responseType={}, elapsedMs={}, bodyLength={}",
                            clazz.getSimpleName(),
                            elapsedMillis(requestStartNanos),
                            jsonResponse == null ? 0 : jsonResponse.length()
                    );
                    return parseResponse(jsonResponse, clazz);
                })
                .doOnSuccess(result -> log.info(
                        "AI 파싱 완료: responseType={}, elapsedMs={}",
                        clazz.getSimpleName(),
                        elapsedMillis(requestStartNanos)
                ))
                .doOnError(error -> log.warn(
                        "AI 호출/파싱 실패: responseType={}, elapsedMs={}, error={}",
                        clazz.getSimpleName(),
                        elapsedMillis(requestStartNanos),
                        rootMessage(error)
                ))
                .toFuture();
    }

    private boolean isRetryableError(Throwable throwable) {
        Throwable root = rootCause(throwable);
        // 서버 과부하/일시 장애(429, 5xx)만 HTTP 레벨 재시도 허용
        if (root instanceof WebClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();
            return statusCode == 429 || responseException.getStatusCode().is5xxServerError();
        }

        // 네트워크/타임아웃 계열은 일시 실패 가능성이 높아 재시도 대상
        return root instanceof WebClientRequestException
                || root instanceof TimeoutException
                || root instanceof SocketTimeoutException
                || root instanceof ConnectException
                || root instanceof IOException;
    }

    private <T> T parseResponse(String jsonResponse, Class<T> clazz) {
        try {
            String generatedText = extractTextFromResponse(jsonResponse);
            String jsonOnly = extractFirstJsonObject(generatedText);
            JsonNode jsonNode = objectMapper.readTree(jsonOnly);
            return objectMapper.treeToValue(jsonNode, clazz);
        } catch (Exception e) {
            throw new GenerateApiException("AI 응답 파싱 중 오류", e);
        }
    }

    static String extractFirstJsonObject(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new GenerateApiException("AI 응답 본문이 비어 있습니다.");
        }

        String text = rawText.trim();
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        int firstBraceIndex = text.indexOf('{');
        if (firstBraceIndex < 0) {
            throw new GenerateApiException("AI 응답에서 JSON 객체 시작('{')을 찾지 못했습니다.");
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        int start = -1;

        for (int i = firstBraceIndex; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }

            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
                continue;
            }

            if (ch == '}') {
                if (depth == 0) {
                    continue;
                }
                depth--;
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1).trim();
                }
            }
        }

        throw new GenerateApiException("AI 응답에서 완전한 JSON 객체를 찾지 못했습니다.");
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "(no error)";
        }

        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        return root.getClass().getSimpleName() + ": " + (message == null ? "(no message)" : message);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        return root == null ? throwable : root;
    }

    protected abstract String getApiUrl();

    protected abstract HttpEntity<Object> createRequestEntity(String prompt);

    protected abstract String extractTextFromResponse(String jsonResponse) throws Exception;
}
