package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicRequest;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
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
        final long requestStartNanos = System.nanoTime();
        final Long memberId = member.getMemberId();
        final String model = request.getModelName();
        final String specExample = request.getSpecExample();
        final String productNameExample = request.getProductNameExample();

        log.info("전자제품 생성 요청 시작: memberId={}, model={}", memberId, model);
        memberService.decrementCredit(memberId);

        final long g2bStartNanos = System.nanoTime();
        CompletableFuture<Optional<String>> g2bFuture = CompletableFuture.supplyAsync(
                        () -> scrapingService.findG2bClassificationNumber(model), taskExecutor)
                .whenComplete((result, throwable) -> {
                    long elapsedMs = elapsedMillis(g2bStartNanos);
                    if (throwable == null) {
                        log.info("단계 완료 - G2B 분류번호 조회: memberId={}, model={}, elapsedMs={}, found={}",
                                memberId, model, elapsedMs, result != null && result.isPresent());
                    } else {
                        log.warn("단계 실패 - G2B 분류번호 조회: memberId={}, model={}, elapsedMs={}, error={}",
                                memberId, model, elapsedMs, rootMessage(throwable));
                    }
                });

        final long countryStartNanos = System.nanoTime();
        CompletableFuture<Optional<String>> countryFuture = CompletableFuture.supplyAsync(
                        () -> scrapingService.findCountryOfOrigin(model), taskExecutor)
                .whenComplete((result, throwable) -> {
                    long elapsedMs = elapsedMillis(countryStartNanos);
                    if (throwable == null) {
                        log.info("단계 완료 - 원산지 조회: memberId={}, model={}, elapsedMs={}, found={}",
                                memberId, model, elapsedMs, result != null && result.isPresent());
                    } else {
                        log.warn("단계 실패 - 원산지 조회: memberId={}, model={}, elapsedMs={}, error={}",
                                memberId, model, elapsedMs, rootMessage(throwable));
                    }
                });

        final long certStartNanos = System.nanoTime();
        CompletableFuture<CertificationResponse> certFuture = aiProviderService.fetchCertification(model)
                .whenComplete((result, throwable) -> {
                    long elapsedMs = elapsedMillis(certStartNanos);
                    if (throwable == null) {
                        log.info("단계 완료 - 인증정보 생성(AI): memberId={}, model={}, elapsedMs={}",
                                memberId, model, elapsedMs);
                    } else {
                        log.warn("단계 실패 - 인증정보 생성(AI): memberId={}, model={}, elapsedMs={}, error={}",
                                memberId, model, elapsedMs, rootMessage(throwable));
                    }
                });

        final long mainSpecStartNanos = System.nanoTime();
        CompletableFuture<GenerateElectronicResponse> mainSpecFuture = aiProviderService
                .fetchMainSpec(model, specExample, productNameExample)
                .whenComplete((result, throwable) -> {
                    long elapsedMs = elapsedMillis(mainSpecStartNanos);
                    if (throwable == null) {
                        log.info("단계 완료 - 메인 스펙 생성(AI): memberId={}, model={}, elapsedMs={}",
                                memberId, model, elapsedMs);
                    } else {
                        log.warn("단계 실패 - 메인 스펙 생성(AI): memberId={}, model={}, elapsedMs={}, error={}",
                                memberId, model, elapsedMs, rootMessage(throwable));
                    }
                });

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

        combinedFuture.whenCompleteAsync((result, throwable) -> {
            long totalElapsedMs = elapsedMillis(requestStartNanos);
            if (throwable != null) {
                log.warn("전자제품 생성 실패. 크레딧 환불. memberId={}, model={}, totalElapsedMs={}, error={}",
                        memberId, model, totalElapsedMs, rootMessage(throwable));
                memberService.restoreCredit(memberId);
            } else {
                log.info("전자제품 생성 완료: memberId={}, model={}, totalElapsedMs={}",
                        memberId, model, totalElapsedMs);
            }
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);

        return combinedFuture;
    }

    @Override
    public CompletableFuture<GenerateNonElectronicResponse> generateGeneralSpec(
            GenerateNonElectronicRequest request,
            Member member
    ) {
        final long requestStartNanos = System.nanoTime();
        final Long memberId = member.getMemberId();
        final String productName = request.getProductName();

        log.info("비전자제품 생성 요청 시작: memberId={}, productName={}", memberId, productName);
        memberService.decrementCredit(memberId);

        CompletableFuture<GenerateNonElectronicResponse> future = aiProviderService
                .fetchGeneralSpec(productName, request.getSpecExample());

        future.whenCompleteAsync((result, throwable) -> {
            long totalElapsedMs = elapsedMillis(requestStartNanos);
            if (throwable != null) {
                log.warn("비전자제품 생성 실패. 크레딧 환불. memberId={}, productName={}, totalElapsedMs={}, error={}",
                        memberId, productName, totalElapsedMs, rootMessage(throwable));
                memberService.restoreCredit(memberId);
            } else {
                log.info("비전자제품 생성 완료: memberId={}, productName={}, totalElapsedMs={}",
                        memberId, productName, totalElapsedMs);
            }
            eventPublisher.publishEvent(new GenerationLogEvent(member, request, result, throwable));
        }, taskExecutor);

        return future;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + ": " + (message == null ? "(no message)" : message);
    }
}
