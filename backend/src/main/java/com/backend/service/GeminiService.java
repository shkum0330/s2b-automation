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

        // ▼▼▼ AI의 정확도를 높이기 위해 프롬프트를 대폭 수정했습니다 ▼▼▼
        String prompt = String.format(
                "당신은 제품 정보를 **매우 정확하게** 조사하고 요약하는 전문가입니다. 다음 작업을 **최대한의 정확성**으로 수행해주세요:\n\n" +
                        "1. **조사 대상 모델명**: '%s'\n" +
                        "2. **규격 형식 예시 (이것은 다른 모델의 정보이며, 오직 형식 참고용입니다)**: '%s'\n\n" +
                        "**작업 지시**:\n" +
                        "1. **정보 조사**: 대상 모델명('%s')의 정확한 실제 정보를 **다음 우선순위에 따라** 조사하세요: **1순위) 제조사 공식 한국어 웹사이트, 2순위) 다나와(danawa.com), 3순위) 네이버 쇼핑**. 다른 블로그나 비공식 출처의 정보는 신뢰하지 마세요.\n" +
                        "2. **정보 검증**: **제조사 공식 정보를 최우선으로 신뢰합니다.** 예를 들어, 공식 사이트에 색상이 '블랙, 화이트'라고 명시되어 있다면, 다른 곳에서 '크림' 색상이 언급되더라도 공식 정보를 따라야 합니다.\n" +
                        "3. **규격 생성**: 조사한 **정확한 정보**를 바탕으로, '규격 형식 예시'와 완벽하게 동일한 스타일과 순서로 새로운 규격을 생성해주세요.\n" +
                        "4. **누락 정보 처리**: 만약 조사 결과 특정 정보가 없다면, 결과물에서 해당 항목을 **과감히 제외**하세요. **절대로 정보를 추측하거나 지어내면 안됩니다.**\n" +
                        "5. **물품명 생성**: 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'을 생성해주세요. (모델명은 포함하지 마세요)\n" +
                        "6. **글자 수 제한**: '물품명'은 **40자 이내**, '규격'은 **50자 이내**로 작성해야 합니다.\n" +
                        "7. **최종 출력 형식**: 최종 결과물은 반드시 아래의 JSON 형식으로만 제공해야 하며, 다른 어떤 설명도 붙이지 마세요:\n" +
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

        String jsonResponse = response.getBody();
        String generatedText = extractGeneratedText(jsonResponse);

        try {
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (JsonProcessingException e) {
            System.err.println("Gemini가 유효하지 않은 JSON을 반환했습니다: " + generatedText);
            GenerateResponse errorResponse = new GenerateResponse();
            errorResponse.setProductName("AI 응답 형식 오류");
            errorResponse.setSpecification("AI가 생성한 원본 텍스트: " + generatedText);
            errorResponse.setModelName(model);
            return errorResponse;
        }
    }

    private String extractGeneratedText(String jsonResponse) throws Exception {
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);

        if (responseMap.containsKey("error")) {
            throw new Exception("Gemini API Error: " + responseMap.get("error").toString());
        }

        return Optional.ofNullable(responseMap.get("candidates"))
                .map(c -> ((List<Map<String, Object>>) c).get(0))
                .map(c -> (Map<String, Object>) c.get("content"))
                .map(c -> ((List<Map<String, Object>>) c.get("parts")).get(0))
                .map(p -> (String) p.get("text"))
                .map(t -> t.replace("```json", "").replace("```", "").trim())
                .orElseThrow(() -> new Exception("Gemini 응답에서 텍스트를 추출할 수 없습니다. 원본 응답: " + jsonResponse));
    }
}
