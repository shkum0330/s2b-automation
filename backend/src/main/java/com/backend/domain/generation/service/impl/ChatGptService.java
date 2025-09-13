package com.backend.domain.generation.service.impl;

import org.springframework.stereotype.Service;

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
