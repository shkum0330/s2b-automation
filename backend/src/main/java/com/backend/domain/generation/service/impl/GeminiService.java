package com.backend.domain.generation.service.impl;

import com.backend.global.exception.GenerateApiException;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Primary
public class GeminiService extends AbstractGenerationService {
    @Value("${gemini.api.url}")
    private String apiUrl;
    @Value("${gemini.api.key}")
    private String apiKey;


    @Value("${gemini.generation.temperature:0.5}")
    private double temperature;
    @Value("${gemini.generation.max-output-tokens:8192}")
    private int maxOutputTokens;

    public GeminiService(PromptBuilder promptBuilder, ObjectMapper objectMapper, WebClient webClient) {
        super(promptBuilder, objectMapper, webClient); // 부모 생성자 변경
    }

    @Override
    protected String getApiUrl() {
        return apiUrl + "?key=" + apiKey;
    }

    @Override
    protected HttpEntity<Map<String, Object>> createRequestEntity(String prompt) {
        Map<String, Object> generationConfig = Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxOutputTokens
        );

        Map<String, Object> googleSearchTool = Map.of("google_search", Map.of());

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig,
                "tools", List.of(googleSearchTool)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }

    @Override
    protected String extractTextFromResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);

        JsonNode candidates = root.path("candidates");
        if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
            throw new GenerateApiException("Gemini 응답에 'candidates' 필드가 없거나 비어있습니다. 응답: " + jsonResponse);
        }

        JsonNode content = candidates.get(0).path("content");
        if (content.isMissingNode()) {
            throw new GenerateApiException("Gemini 응답에 'content' 필드가 없습니다.");
        }

        JsonNode parts = content.path("parts");
        if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
            throw new GenerateApiException("Gemini 응답에 'parts' 필드가 없거나 비어있습니다.");
        }

        JsonNode textNode = parts.get(0).path("text");
        if (textNode.isMissingNode()) {
            throw new GenerateApiException("Gemini 응답에 'text' 필드가 없습니다.");
        }

        return textNode.asText().replace("```json", "").replace("```", "").trim();
    }
}