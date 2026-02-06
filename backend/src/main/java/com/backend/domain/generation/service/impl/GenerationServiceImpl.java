package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.*;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.domain.generation.service.GenerationService;
import com.backend.domain.generation.service.ScrapingService;
import com.backend.domain.log.event.GenerationLogEvent;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class GenerationServiceImpl implements GenerationService {
    private final AiProviderService aiProviderService;
    private final ScrapingService scrapingService;
    private final MemberService memberService;
    private final Executor taskExecutor;
    private final ApplicationEventPublisher eventPublisher;

    // 1. 전자제품 스펙 생성
    @Override
    public CompletableFuture<GenerateElectronicResponse> generateSpec(GenerateElectronicRequest request, Member member) {
        // 크레딧 선 차감 (낙관적 차감)
        memberService.decrementCredit(member.getMemberId());

        String model = request.getModelName();
        String specExample = request.getSpecExample();
        String productNameExample = request.getProductNameExample();

        // 외부 API 요청 병렬 실행 (Non-blocking)
        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(() ->
                scrapingService.findG2bClassificationNumber(model), taskExecutor);
        CompletableFuture<Optional<String>> countryFuture = CompletableFuture.supplyAsync(() ->
                scrapingService.findCountryOfOrigin(model), taskExecutor);
        CompletableFuture<CertificationResponse> certFuture = aiProviderService.fetchCertification(model);
        CompletableFuture<GenerateElectronicResponse> mainSpecFuture = aiProviderService.fetchMainSpec(model, specExample, productNameExample);

        // 각 작업이 끝나는 대로 결과에 반영되며, 모든 작업이 완료되면 최종 결과가 나옴
        CompletableFuture<GenerateElectronicResponse> combinedFuture = mainSpecFuture
                .thenCombineAsync(certFuture, (mainSpec, cert) -> {
                    mainSpec.setCertificationNumber(cert);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(g2bFuture, (mainSpec, g2bOpt) -> {
                    g2bOpt.ifPresent(mainSpec::setG2bClassificationNumber);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(countryFuture, (mainSpec, countryOpt) -> {
                    countryOpt.ifPresent(mainSpec::setCountryOfOrigin);
                    return mainSpec;
                }, taskExecutor);

        // 후처리 (로깅 및 실패 시 보상 트랜잭션)
        combinedFuture.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                log.warn("전자제품 생성 실패. 크레딧 환불. memberId={}, error={}", member.getMemberId(), throwable.getMessage());
                // 실패 시 크레딧 복구
                memberService.restoreCredit(member.getMemberId());
            }
            // 성공/실패 여부와 관계없이 이력 저장 (Event 발행)
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);

        return combinedFuture;
    }

    // 2. 비전자제품 스펙 생성
    @Override
    public CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(GenerateNonElectronicRequest request, Member member) {
        // 크레딧 선 차감
        memberService.decrementCredit(member.getMemberId());

        // AI 생성 요청 (비동기)
        CompletableFuture<GenerateNonElectronicResponse> future = aiProviderService.fetchGeneralSpec(request.getProductName(), request.getSpecExample());

        //  후처리
        future.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                log.warn("비전자제품 생성 실패. 크레딧 환불. memberId={}, error={}", member.getMemberId(), throwable.getMessage());
                memberService.restoreCredit(member.getMemberId());
            }
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);

        return future;
    }
}