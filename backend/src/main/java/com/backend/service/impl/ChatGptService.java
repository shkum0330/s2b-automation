package com.backend.service.impl;

import com.backend.exception.GenerateApiException;
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
public class ChatGptService {
//    @Value("${chatgpt.api.url}")
//    private String apiUrl;
//    @Value("${chatgpt.api.key}")
//    private String apiKey;
//
//    public ChatGptService(ScrapingService scrapingService, PromptBuilder promptBuilder, ObjectMapper objectMapper, Executor taskExecutor) {
//        super(scrapingService, promptBuilder, objectMapper, taskExecutor);
//    }
//
//    @Override
//    protected String getApiUrl() {
//        return apiUrl;
//    }
//
//    @Override
//    protected HttpEntity<Map<String, Object>> createRequestEntity(String prompt) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.setBearerAuth(apiKey);
//
//        Map<String, Object> message = Map.of("role", "user", "content", prompt);
//        Map<String, Object> requestBody = Map.of(
//                "model", "gpt-4o",
//                "messages", List.of(message),
//                "response_format", Map.of("type", "json_object")
//        );
//        return new HttpEntity<>(requestBody, headers);
//    }
//
//    @Override
//    protected String extractTextFromResponse(String jsonResponse) throws Exception {
//        Map<String, Object> responseMap = getObjectMapper().readValue(jsonResponse, Map.class);
//        return Optional.ofNullable(responseMap.get("choices"))
//                .map(c -> ((List<Map<String, Object>>) c).get(0))
//                .map(c -> (Map<String, Object>) c.get("message"))
//                .map(m -> (String) m.get("content"))
//                .orElseThrow(() -> new GenerateApiException("ChatGPT 응답에서 'content' 필드를 추출할 수 없습니다."));
//    }
//
//    @Override
//    protected String getApiName() {
//        return "ChatGPT";
//    }
}
