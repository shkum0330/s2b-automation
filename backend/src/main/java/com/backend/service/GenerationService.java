package com.backend.service;

import com.backend.dto.GenerateResponse;

public interface GenerationService {
    /**
     * 모델명과 예시를 기반으로 제품 사양 정보를 생성
     *
     * @param model              조사할 제품의 모델명
     * @param specExample        참고할 규격 형식 예시
     * @param productNameExample 참고할 물품명 형식 예시
     * @return 생성된 제품 정보가 담긴 GenerateResponse 객체
     * @throws Exception API 호출 또는 데이터 처리 중 발생할 수 있는 예외
     */
    GenerateResponse generateSpec(String model, String specExample, String productNameExample) throws Exception;

//    /**
//     * 전자제품 모델명으로 인증 정보를 생성
//     *
//     * @param model              조사할 제품의 모델명
//     * @return 생성된 인증 정보가 담긴 CertificationResponse 객체
//     * @throws Exception API 호출 또는 데이터 처리 중 발생할 수 있는 예외
//     */
//    CertificationResponse findCertificationNumbers(String model) throws Exception;
}
