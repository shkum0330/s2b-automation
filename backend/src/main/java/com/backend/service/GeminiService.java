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

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerateResponse generateSpec(String model, String specExample, String productNameExample) throws Exception {

        String fullApiUrl = apiUrl + "?key=" + apiKey;
        String promptHeader;
        String productNameInstruction;

        if (productNameExample != null && !productNameExample.isBlank()) {
            // productNameExample이 제공된 경우
            promptHeader = String.format(
                    "2. **물품명 형식 예시 (참고용)**: '%s'\n" +
                            "3. **규격 형식 예시 (참고용)**: '%s'\n\n",
                    productNameExample, specExample
            );
            productNameInstruction = "2. **물품명 생성**: 조사한 정보를 바탕으로, **주어진 '물품명 형식 예시'와 완벽하게 동일한 스타일과 형식으로** 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'을 생성해주세요. (모델명은 포함하지 마세요)\n";
        } else {
            // productNameExample이 제공되지 않은 경우
            promptHeader = String.format(
                    "2. **규격 형식 예시 (참고용)**: '%s'\n\n",
                    specExample
            );
            productNameInstruction = "2. **물품명 생성**: 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'을 생성해주세요. (모델명은 포함하지 마세요)\n";
        }

        // 2. 동적으로 생성된 변수들을 최종 프롬프트에 적용
        String prompt = String.format(
                "당신은 제품 정보를 **극도로 정확하게** 조사하고 검증하는 **최고 수준의 데이터 전문가**입니다. 다음 작업을 **최대한의 정확성**으로 수행해주세요:\n\n" +
                        "1. **조사 대상 모델명**: '%s'\n" +
                        "%s" + // 동적으로 생성된 프롬프트 헤더
                        "**작업 지시**:\n" +
                        "1. **정보 조사**: 대상 모델명('%s')의 실제 정보를 **다음 우선순위에 따라** 조사하세요: **1순위) 제조사 공식 한국어 웹사이트, 2순위) 다나와(danawa.com), 3순위) KC인증정보 검색서비스**. 다른 출처는 신뢰하지 마세요.\n" +
                        "%s" + // 동적으로 생성된 물품명 지시사항
                        "3. **G2B 물품목록번호 검색**: '나라장터 목록정보시스템'에서 모델명 '%s'의 '물품식별번호'를 찾아주세요. 이것이 'G2B 물품목록번호'입니다. **정보가 없으면 반드시 빈 문자열(\"\")로 값을 설정해야 합니다.**\n" +
                        "4. **인증번호 정밀 검증**: 웹사이트에서 '**안전인증번호**'나 '**전파적합성인증번호**'를 찾되, **반드시 인증 문서에서 조사 대상 모델명('%s')이 명확히 언급되는지 확인해야 합니다.** 관련 없는 번호는 절대 사용하면 안 됩니다. 없으면 무조건 빈 문자열(\"\")로 처리하세요.\n" +
                        "5. **추가 정보 조사**: '제조사'와 '원산지' 정보를 찾아주세요. 없으면 빈 문자열(\"\")로 처리하세요.\n" +
                        "6. **가격 정보 수집**: '다나와'와 '네이버쇼핑'에서 **최저가 순으로 최대 10개**의 가격 정보를 찾아, 판매처 이름('storeName'), 가격('price'), 판매 페이지 링크('storeLink')를 수집해주세요. 없으면 빈 배열([])로 처리하세요.\n" +
                        "7. **규격 생성**: 검증된 정보를 바탕으로, '규격 형식 예시'에 맞춰 '규격'을 생성해주세요.\n" +
                        "8. **글자 수 제한**: '물품명'은 **40자 이내**, '규격'은 **50자 이내**여야 합니다.\n" +
                        "9. **최종 출력 형식**: 최종 결과물은 반드시 아래의 JSON 형식으로만 제공해야 하며, 다른 어떤 설명도 붙이지 마세요:\n" +
                        "{\"productName\":\"생성된 물품명\",\"specification\":\"생성된 규격\",\"modelName\":\"%s\",\"katsCertificationNumber\":\"찾아낸 전기안전인증번호\",\"kcCertificationNumber\":\"찾아낸 전파적합성인증번호\",\"manufacturer\":\"찾아낸 제조사\",\"countryOfOrigin\":\"찾아낸 원산지\",\"priceList\":[],\"g2bClassificationNumber\":\"찾아낸 G2B 물품목록번호\"}",
                model, promptHeader, model, productNameInstruction, model, model, model
        );

        try {
            Map<String, Object> requestBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(fullApiUrl, entity, String.class);
            String jsonResponse = response.getBody();
            String generatedText = extractGeneratedText(jsonResponse);
            return objectMapper.readValue(generatedText, GenerateResponse.class);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            throw new GeminiApiException("Gemini API가 과부하 상태입니다. 잠시 후 다시 시도해주세요.");
        } catch (JsonProcessingException e) {
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
