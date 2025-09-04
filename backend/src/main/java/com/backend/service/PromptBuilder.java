package com.backend.service;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    // 템플릿 1: '인증번호' 관련 내용이 '완전히' 제거된 메인 정보 조회용 프롬프트
    private static final String PRODUCT_SPEC_PROMPT_TEMPLATE = """
        당신은 제품 정보를 극도로 정확하게 조사·검증하는 데이터 전문가입니다.
        아래 원칙을 반드시 지키고, 확증되지 않은 정보는 공백으로 처리하세요.
        
        [허용 출처]
        1) 제조사 공식 한국어 웹사이트 2) 다나와(danawa.com) 3) 네이버 플러스 스토어(http://shopping.naver.com/)
        → 위 3개 이외의 출처는 절대 사용 금지.
        
        [모델 매칭 규칙]
        - 입력 모델명과 완전 일치(대소문자/공백/하이픈 차이만 허용)하는 경우만 인정.
        - 시리즈/유사모델/옵션만 표기된 페이지는 불인정.
        - 문서·표·사양표 등 화면 내에 정확한 모델명이 직접 표기되어야 함.
        
        [제조사·원산지 규칙]
        - 제조사 공식 표기를 최우선으로 사용. 없으면 다나와/다수 쇼핑몰 교차검증. 불명확하면 빈 문자열("").
        
        [출력 규칙 및 자기검증]
        - productName은 모델명 미포함, 40자 이내. specification은 50자 이내.
        - manufacturer를 먼저 결정하고, productName은 반드시 manufacturer로 시작해야 함.
        - 예시의 브랜드/시리즈명은 스타일 참고용일 뿐, 절대 복사하지 말 것.
        - specification은 허용 출처에서 확인된 항목만 사용(미확인 스펙은 누락). 구분자는 " / " 유지.
        - 어떤 항목이든 확증 불가 시 빈 문자열("")로 반환.
        - 반드시 JSON만 출력(부가 설명 금지).
        
        입력 모델명: '%s'
        %s
        
        작업 지시:
        1) 정보 조사: 모델명('%s')의 실제 정보를 허용 출처 순서대로만 조사.
        %s
        2) 추가 정보: manufacturer, countryOfOrigin을 규칙대로 조사, 불명확하면 빈 문자열.
        3) 규격 생성: 검증된 사실만으로 50자 이내 핵심 스펙 작성(예시 형식은 스타일만 참고).
        4) 최종 출력: 아래 JSON만 반환.
        
        {"productName":"생성된 물품명","specification":"생성된 규격","modelName":"%s","manufacturer":"찾아낸 제조사","countryOfOrigin":"찾아낸 원산지"}
        """;

    // 템플릿 2: 인증번호 조회용 (변경 없음)
    private static final String CERTIFICATION_PROMPT_TEMPLATE = """
        %s 모델의 "국가기술표준원 인증번호"와 "KC 전파적합성인증번호"를 찾아오세요.
        결과가 여러 개일 경우 하나만 가져와야 합니다. 단 우선순위는 맨 끝자리가 알파벳이 붙은 겁니다.
        찾지 못하거나, 모델명 매칭이 불확실하면 반드시 빈 문자열("")로 처리해야 합니다.

        [출력 형식]
        다른 설명 없이, 아래 JSON 형식만 반환하세요.
        ```json
        {"katsCertificationNumber":"(찾아낸 국가기술표준원 인증번호)","kcCertificationNumber":"(찾아낸 KC 전파적합성인증번호)"}
        ```""";


    public String buildProductSpecPrompt(String model, String specExample, String productNameExample) {
        String promptHeader;
        String productNameInstruction;

        if (productNameExample != null && !productNameExample.isBlank()) {
            promptHeader = String.format(
                    "2. 물품명 형식 예시 (참고용): '%s'\\n" +
                            "3. 규격 형식 예시 (참고용): '%s'\\n\\n",
                    productNameExample, specExample
            );
            productNameInstruction = "2) 물품명 생성: manufacturer를 먼저 확정 후, 예시의 스타일만 참고하여 '<제조사> <공식 시리즈/제품명> <카테고리>' 형식으로 40자 이내 작성. " +
                    "- 모델명은 포함 금지, 예시의 브랜드/시리즈 복사 금지. manufacturer가 빈 문자열이면 productName도 빈 문자열 처리.\n";
        } else {
            promptHeader = String.format(
                    "2. 규격 형식 예시 (참고용): '%s'\\n\\n",
                    specExample
            );
            productNameInstruction = "2. 물품명 생성: 대상 모델에 맞는 간결하고 정확한 한국어 물품명을 생성 (모델명은 제외)\\n";
        }

        return String.format(
                PRODUCT_SPEC_PROMPT_TEMPLATE, // 상수 사용
                model,
                promptHeader,
                model,
                productNameInstruction,
                model,
                model
        );
    }

    public String buildCertificationPrompt(String model) {
        // 분리된 상수를 사용하여 메서드가 간결해짐
        return String.format(CERTIFICATION_PROMPT_TEMPLATE, model);
    }
}
