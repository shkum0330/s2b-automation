package com.backend.domain.generation.service;

import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.member.entity.Member;
import com.backend.global.exception.GenerateApiException;

import java.util.concurrent.CompletableFuture;

public interface GenerationService {
    /**
     * 전자제품 모델명과 예시를 기반으로 제품 사양 정보를 생성
     *
     * @param model              조사할 제품의 모델명
     * @param specExample        참고할 규격 형식 예시
     * @param productNameExample 참고할 물품명 형식 예시
     * @param member             요청 회원(크레딧 차감)
     * @return 생성된 제품 정보가 담긴 GenerateResponse 객체
     * @throws GenerateApiException API 호출 또는 데이터 처리 중 발생할 수 있는 예외
     */
    CompletableFuture<GenerateElectronicResponse> generateSpec(String model, String specExample, String productNameExample, Member member) throws GenerateApiException;

    /**
     * 비전자제품 제품명과 예시를 기반으로 제품 사양 정보를 생성
     *
     * @param specExample        참고할 규격 형식 예시
     * @param productName        물품명
     * @param member             요청 회원(크레딧 차감)
     * @return 생성된 제품 정보가 담긴 GenerateGeneralResponse 객체
     * @throws GenerateApiException API 호출 또는 데이터 처리 중 발생할 수 있는 예외
     */
    CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(String productName, String specExample, Member member) throws GenerateApiException;
//    /**
//     * 전자제품 모델명으로 인증 정보를 생성
//     *
//     * @param model              조사할 제품의 모델명
//     * @return 생성된 인증 정보가 담긴 CertificationResponse 객체
//     * @throws Exception API 호출 또는 데이터 처리 중 발생할 수 있는 예외
//     */
//    CertificationResponse findCertificationNumbers(String model) throws Exception;
}
