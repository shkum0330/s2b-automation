package com.backend.service;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    // 1. 긴 프롬프트 문자열을 텍스트 블록을 사용해 상수로 분리
    private static final String PROMPT_TEMPLATE = """
            당신은 제품 정보를 **극도로 정확하게** 조사하고 검증하는 **최고 수준의 데이터 전문가**입니다. 다음 작업을 **최대한의 정확성**으로 수행해주세요:

            1. **조사 대상 모델명**: '%s'
            %s
            **작업 지시**:
            1. **정보 조사**: 대상 모델명('%s')의 실제 정보를 **다음 우선순위에 따라** 조사하세요: **1순위) 제조사 공식 한국어 웹사이트, 2순위) 다나와(danawa.com), 3순위) KC인증정보 검색서비스**. 다른 출처는 신뢰하지 마세요.
            %s
            2. **인증번호 정밀 검증**: 해당 모델의 '**국가기술표언 인증번호**'와 '**KC 전파적합성인증번호**'를 찾되, **반드시 인증 문서에서 조사 대상 모델명('%s')이 명확히 언급되는지 확인해야 합니다.** 관련 없는 번호는 절대 사용하면 안 됩니다. 없으면 무조건 빈 문자열("")로 처리하세요.
            3. **추가 정보 조사**: '제조사'와 '원산지' 정보를 찾아주세요. 원산지의 신뢰도는 제조사가 공식적으로 밝히는 것을 최우선으로 삼고, 거기서 못 찾았으면 많은 쇼핑몰에서 제시하는 것으로 삼아주세요. 없으면 빈 문자열("")로 처리하세요.
            4. **가격 정보 수집**: '다나와'와 '네이버쇼핑'에서 **최저가 순으로 최대 10개**의 가격 정보를 찾아, 판매처 이름('storeName'), 가격('price'), 판매 페이지 링크('storeLink')를 수집해주세요. 없으면 빈 배열([])로 처리하세요.
            5. **규격 생성**: 검증된 정보를 바탕으로, '규격 형식 예시'에 맞춰 '규격'을 생성해주세요.
            6. **글자 수 제한**: '물품명'은 **40자 이내**, '규격'은 **50자 이내**여야 합니다.
            7. **최종 출력 형식**: 최종 결과물은 반드시 아래의 JSON 형식으로만 제공해야 하며, 다른 어떤 설명도 붙이지 마세요:
            {"productName":"생성된 물품명","specification":"생성된 규격","modelName":"%s","katsCertificationNumber":"찾아낸 전기안전인증번호","kcCertificationNumber":"찾아낸 전파적합성인증번호","manufacturer":"찾아낸 제조사","countryOfOrigin":"찾아낸 원산지","priceList":[]}""";


    public String buildProductSpecPrompt(String model, String specExample, String productNameExample) {
        String promptHeader;
        String productNameInstruction;

        if (productNameExample != null && !productNameExample.isBlank()) {
            promptHeader = String.format(
                    "2. **물품명 형식 예시 (참고용)**: '%s'\\n" +
                            "3. **규격 형식 예시 (참고용)**: '%s'\\n\\n",
                    productNameExample, specExample
            );
            productNameInstruction = "2. **물품명 생성**: 조사한 정보를 바탕으로, **주어진 '물품명 형식 예시'와 완벽하게 동일한 스타일과 형식으로** 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'을 생성해주세요. (모델명은 포함하지 마세요)\\n";
        } else {
            promptHeader = String.format(
                    "2. **규격 형식 예시 (참고용)**: '%s'\\n\\n",
                    specExample
            );
            productNameInstruction = "2. **물품명 생성**: 대상 모델에 맞는 간결하고 정확한 한국어 '물품명'을 생성해주세요. (모델명은 포함하지 마세요)\\n";
        }

        // 2. 분리된 상수를 사용하여 가독성 높은 코드로 변경
        return String.format(
                PROMPT_TEMPLATE,
                model,
                promptHeader,
                model,
                productNameInstruction,
                model,
                model
        );
    }
}
