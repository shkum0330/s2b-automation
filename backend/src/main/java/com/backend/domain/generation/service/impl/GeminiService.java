package com.backend.domain.generation.service.impl;

import com.backend.global.exception.GenerateApiException;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
        super(promptBuilder, objectMapper, webClient);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiRequest(List<Content> contents, GenerationConfig generationConfig, List<Tool> tools) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GenerationConfig(double temperature, int maxOutputTokens) {
    }

    private record Tool(Map<String, Object> google_search) {
    }

    @Override
    protected String getApiUrl() {
        return apiUrl + "?key=" + apiKey;
    }

    @Override
    protected HttpEntity<Object> createRequestEntity(String prompt) {
        GenerationConfig config = new GenerationConfig(temperature, maxOutputTokens);
        Content content = new Content(List.of(new Part(prompt)));
        Tool googleSearch = new Tool(Map.of());

        GeminiRequest requestBody = new GeminiRequest(
                List.of(content),
                config,
                List.of(googleSearch)
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

        StringBuilder combinedText = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode textNode = part.path("text");
            if (!textNode.isMissingNode()) {
                String partText = textNode.asText();
                if (StringUtils.hasText(partText)) {
                    if (!combinedText.isEmpty()) {
                        combinedText.append('\n');
                    }
                    combinedText.append(partText.trim());
                }
            }
        }

        if (combinedText.isEmpty()) {
            throw new GenerateApiException("Gemini 응답에 'text' 필드가 없습니다.");
        }

        return combinedText.toString();
    }
}
