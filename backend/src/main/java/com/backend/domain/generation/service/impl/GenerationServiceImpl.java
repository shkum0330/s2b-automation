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

    @Override
    public CompletableFuture<GenerateElectronicResponse> generateSpec(GenerateElectronicRequest request, Member member) {
        memberService.decrementCredit(member.getMemberId());

        String model = request.getModelName();
        String specExample = request.getSpecExample();
        String productNameExample = request.getProductNameExample();

        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(() -> scrapingService.findG2bClassificationNumber(model), taskExecutor);
        CompletableFuture<Optional<String>> countryOfOriginFuture = CompletableFuture.supplyAsync(() -> scrapingService.findCountryOfOrigin(model), taskExecutor);
        CompletableFuture<CertificationResponse> certFuture = aiProviderService.fetchCertification(model);
        CompletableFuture<GenerateElectronicResponse> mainSpecFuture = aiProviderService.fetchMainSpec(model, specExample, productNameExample);

        CompletableFuture<GenerateElectronicResponse> combinedFuture = mainSpecFuture
                .thenCombineAsync(certFuture, (mainSpec, cert) -> {
                    mainSpec.setCertificationNumber(cert);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(g2bFuture, (mainSpec, g2bOpt) -> {
                    g2bOpt.ifPresent(mainSpec::setG2bClassificationNumber);
                    return mainSpec;
                }, taskExecutor)
                .thenCombineAsync(countryOfOriginFuture, (mainSpec, countryOpt) -> {
                    countryOpt.ifPresent(mainSpec::setCountryOfOrigin);
                    return mainSpec;
                }, taskExecutor);


        combinedFuture.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                // 실패 시 보상 트랜잭션 실행
                log.warn("전자제품 생성 실패. 크레딧 환불 진행. memberId={}", member.getMemberId(), throwable);
                memberService.restoreCredit(member.getMemberId());
            }

            // 로그 이벤트 발행
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);

        return combinedFuture;
    }

    @Override
    public CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(GenerateNonElectronicRequest request, Member member) {
        memberService.decrementCredit(member.getMemberId());

        String productName = request.getProductName();
        String specExample = request.getSpecExample();

        CompletableFuture<GenerateNonElectronicResponse> future = aiProviderService.fetchGeneralSpec(productName, specExample);

        future.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                log.warn("비전자제품 생성 실패. 크레딧 환불 진행. memberId={}", member.getMemberId(), throwable);
                memberService.restoreCredit(member.getMemberId());
            }
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);
        return future;
    }
}