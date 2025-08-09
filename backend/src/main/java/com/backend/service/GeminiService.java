package com.backend.service;

import com.backend.exception.GeminiApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.GenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeminiService {
    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerateResponse generateSpec(String model, String example) throws Exception {

        String prompt = String.format(
                "당신은 제품 정보를 **극도로 정확하게** 조사하고 검증하는 **최고 수준의 전문가**입니다. 다음 작업을 **최대한의 정확성**으로 수행해주세요:\n\n" +
                        "1. **조사 대상 모델명**: '%s'\n" +
                        "2. **규격 형식 예시 (오직 형식 참고용)**: '%s'\n\n" +
                        "**작업 지시**:\n" +
                        "1. **정보 조사**: 대상 모델명('%s')의 실제 정보를 **다음 우선순위에 따라** 조사하세요: **1순위) 제조사 공식 한국어 웹사이트, 2순위) 다나와(danawa.com), 3순위) KC인증정보 검색서비스**. 다른 출처는 신뢰하지 마세요.\n" +
                        "2. **인증번호 정밀 검색**: 조사 과정에서 다음 두 가지 인증번호를 찾아주세요.\n" +
                        "   - **전기안전인증번호**: 웹사이트에서 '**안전인증번호**', '**전기용품안전인증**' 번호를 찾아주세요. 이것이 '국가기술표준원 인증번호'입니다.\n" +
                        "   - **전파적합성인증번호**: 웹사이트에서 '**방송통신기자재 적합성평가**' 번호를 찾아주세요. 이것이 'KC 전파적합성인증번호'입니다.\n" +
                        "3. **정보 검증**: **찾아낸 모든 정보가 조사 대상 모델명('%s')과 명확하게 연결되는지 반드시 교차 검증해야 합니다.** 관련 없는 정보는 절대 사용하지 마세요.\n" +
                        "4. **누락 정보 처리**: 만약 우선순위 소스를 모두 확인했음에도 특정 정보(특히 인증번호)를 **찾을 수 없다면**, 해당 JSON 값은 반드시 **빈 문자열(\"\")**로 설정해야 합니다. **절대 추측하거나 비슷한 다른 번호로 대체하지 마세요.**\n" +
                        "5. **규격 및 물품명 생성**: 검증된 정보를 바탕으로, '규격 형식 예시'에 맞춰 '규격'을 생성하고, 모델명을 제외한 '물품명'을 생성해주세요.\n" +
                        "6. **글자 수 제한**: '물품명'은 **40자 이내**, '규격'은 **50자 이내**여야 합니다.\n" +
                        "7. **최종 출력 형식**: 최종 결과물은 반드시 아래의 JSON 형식으로만 제공해야 하며, 다른 어떤 설명도 붙이지 마세요:\n" +
                        "{\"productName\": \"생성된 물품명\", \"specification\": \"생성된 규격\", \"modelName\": \"%s\", \"katsCertificationNumber\": \"찾아낸 전기안전인증번호\", \"kcCertificationNumber\": \"찾아낸 전파적합성인증번호\"}",
                model, example, model, model, model
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


        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            String jsonResponse = response.getBody();
            String generatedText = extractGeneratedText(jsonResponse);
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            // 503 예외
            throw new GeminiApiException("Gemini API가 과부하 상태입니다. 잠시 후 다시 시도해주세요.");
        } catch (JsonProcessingException e) {
            // JSON 파싱 오류
            throw new GeminiApiException("AI가 생성한 응답의 형식이 잘못되었습니다.");
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
