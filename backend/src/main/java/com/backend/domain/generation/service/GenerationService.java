package com.backend.domain.generation.service;

import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicRequest;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.member.entity.Member;
import com.backend.global.exception.GenerateApiException;

import java.util.concurrent.CompletableFuture;

public interface GenerationService {
    /**
     * 전자제품 모델명과 예시를 기반으로 제품 사양 정보를 생성
     *
     * @param request DTO 객체 (modelName, specExample, productNameExample 포함)
     * @param member  요청 회원(크레딧 차감)
     * @return 생성된 제품 정보가 담긴 GenerateResponse 객체
     * @throws GenerateApiException API 호출 또는 데이터 처리 중 발생할 수 있는 예외
     */
    CompletableFuture<GenerateElectronicResponse> generateSpec(GenerateElectronicRequest request, Member member) throws GenerateApiException;

    /**
     * 비전자제품 제품명과 예시를 기반으로 제품 사양 정보를 생성
     *
     * @param request DTO 객체 (productName, specExample 포함)
     * @param member  요청 회원(크레딧 차감)
     * @return 생성된 제품 정보가 담긴 GenerateGeneralResponse 객체
     * @throws GenerateApiException API 호출 또는 데이터 처리 중 발생할 수 있는 예외
     */
    CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(GenerateNonElectronicRequest request, Member member) throws GenerateApiException;
}
