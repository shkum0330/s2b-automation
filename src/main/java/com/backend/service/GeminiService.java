package com.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.GenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeminiService {
    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerateResponse generateSpec(String model, String example) throws Exception {
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        // Gemini API에 보낼 프롬프트를 새롭게 구성합니다.
        String prompt = String.format(
                "당신은 제품 정보를 정확하게 요약하고 형식을 맞추는 전문가입니다. 다음 작업을 수행해주세요:\n\n" +
                        "1. 대상 모델명: '%s'\n" +
                        "2. 내가 원하는 규격의 형식 예시 (이것은 다른 모델의 정보임): '%s'\n\n" +
                        "작업 지시:\n" +
                        "- 대상 모델명('%s')의 정확한 실제 정보를 조사하세요. 정보 소스는 제조사 공식 홈페이지, 다나와, 네이버 쇼핑 순으로 신뢰합니다.\n" +
                        "- 조사한 정보를 바탕으로, 내가 제시한 '규격 형식 예시'와 완벽하게 동일한 스타일과 순서로 새로운 규격을 생성해주세요.\n" +
                        "- 조사 결과 정보가 없는 항목은 결과물에서 제외하세요. 절대로 정보를 추측하거나 지어내면 안됩니다.\n" +
                        "- 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'도 함께 생성해주세요.\n" +
                        "- **생성하는 '물품명'의 길이는 반드시 40자 이내여야 합니다.**\n" +
                        "- **생성하는 '규격'의 길이는 반드시 50자 이내여야 합니다.**\n" +
                        "- 최종 결과물은 반드시 아래의 JSON 형식으로만 제공해야 하며, 다른 어떤 설명도 붙이지 마세요:\n" +
                        "{\"productName\": \"생성된 물품명\", \"specification\": \"생성된 규격\", \"modelName\": \"%s\"}",
                model, example, model, model
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        // Gemini 응답에서 순수 텍스트(AI가 생성한 JSON)를 추출합니다.
        String generatedText = extractGeneratedText(response.getBody());

        // Gemini가 생성한 JSON 문자열을 GenerateResponse 객체로 변환합니다.
        // 정보가 없는 경우를 대비해 필드를 비워둘 수 있도록 처리합니다.
        try {
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (JsonProcessingException e) {
            System.err.println("Gemini가 유효하지 않은 JSON을 반환했습니다: " + generatedText);
            // AI가 JSON 형식을 반환하지 못했을 경우의 예외 처리
            GenerateResponse errorResponse = new GenerateResponse();
            errorResponse.setProductName("AI 응답 형식 오류");
            errorResponse.setSpecification("AI가 생성한 원본 텍스트: " + generatedText);
            errorResponse.setModelName(model);
            return errorResponse;
        }
    }

    // Gemini API의 복잡한 응답 구조에서 최종 텍스트만 추출하는 헬퍼 함수
    private String extractGeneratedText(String jsonResponse) throws Exception {
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        return Optional.ofNullable(responseMap.get("candidates"))
                .map(c -> ((List<Map<String, Object>>) c).get(0))
                .map(c -> (Map<String, Object>) c.get("content"))
                .map(c -> ((List<Map<String, Object>>) c.get("parts")).get(0))
                .map(p -> (String) p.get("text"))
                .map(t -> t.replace("```json", "").replace("```", "").trim()) // Markdown 형식 제거
                .orElseThrow(() -> new Exception("Gemini 응답에서 텍스트를 추출할 수 없습니다."));
    }
}
