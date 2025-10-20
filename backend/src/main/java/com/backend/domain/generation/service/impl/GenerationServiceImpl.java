package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.domain.generation.service.GenerationService;
import com.backend.domain.generation.service.ScrapingService;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public CompletableFuture<GenerateElectronicResponse> generateSpec(String model, String specExample, String productNameExample, Member member) {
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

        combinedFuture.whenCompleteAsync((result, throwable) -> handleCreditDeduction(member, throwable, "전자제품"), taskExecutor);

        return combinedFuture;
    }

    @Override
    public CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(String productName, String specExample, Member member) {
        CompletableFuture<GenerateNonElectronicResponse> future = aiProviderService.fetchGeneralSpec(productName, specExample);
        future.whenCompleteAsync((result, throwable) -> handleCreditDeduction(member, throwable, "비전자제품"), taskExecutor);
        return future;
    }

    private void handleCreditDeduction(Member member, Throwable throwable, String type) {
        if (throwable == null) {
            try {
                memberService.decrementCredit(member.getMemberId());
                log.info("{} 생성 성공. 사용자(ID: {}) 크레딧 차감.", type, member.getMemberId());
            } catch (Exception e) {
                log.error("{} 생성 성공 후 크레딧 차감 중 에러 발생. 사용자 ID: {}", type, member.getMemberId(), e);
            }
        } else {
            log.warn("{} 생성 실패로 사용자(ID: {}) 크레딧을 차감하지 않았습니다.", type, member.getMemberId(), throwable);
        }
    }
}
