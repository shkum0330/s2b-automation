package com.backend.service.impl;

import com.backend.service.PromptBuilder;
import com.backend.service.ScrapingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
@Primary
public class GeminiService extends AbstractGenerationService {
    @Value("${gemini.api.url}")
    private String apiUrl;
    @Value("${gemini.api.key}")
    private String apiKey;


    public GeminiService(ScrapingService scrapingService, PromptBuilder promptBuilder, ObjectMapper objectMapper, RestTemplate restTemplate, Executor taskExecutor) {
        super(scrapingService, promptBuilder, objectMapper, restTemplate, taskExecutor);
    }


    @Override
    protected String getApiUrl() {
        return apiUrl + "?key=" + apiKey;
    }

    @Override
    protected HttpEntity<Map<String, Object>> createRequestEntity(String prompt) {
        // 1. generationConfig 맵 수정
        Map<String, Object> generationConfig = Map.of(
                "temperature", 0.3,
                // "topP", 0.95,
                "maxOutputTokens", 65536,
                "response_mime_type", "application/json"
        );

//        // 2. tools 파라미터 추가 (Grounding with Google Search 활성화)
//        Map<String, Object> googleSearchTool = Map.of(
//                "google_search_retrieval", Map.of()
//        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", generationConfig
//                "tools", List.of(googleSearchTool) // (추가)
        );


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }


    @Override
    protected String extractTextFromResponse(String jsonResponse) throws Exception {
        Map<String, Object> responseMap = new ObjectMapper().readValue(jsonResponse, Map.class);
        return Optional.ofNullable(responseMap.get("candidates"))
                .map(c -> ((List<Map<String, Object>>) c).get(0))
                .map(c -> (Map<String, Object>) c.get("content"))
                .map(c -> ((List<Map<String, Object>>) c.get("parts")).get(0))
                .map(p -> (String) p.get("text"))
                .map(t -> t.replace("```json", "").replace("```", "").trim())
                .orElseThrow(() -> new Exception("Gemini 응답에서 텍스트를 추출할 수 없습니다."));
    }


    @Override
    protected String getApiName() {
        return "Gemini";
    }
}
