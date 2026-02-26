package com.backend.domain.generation.service.impl;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.domain.generation.service.ScrapingService;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationServiceImplFallbackTest {

    @Test
    @DisplayName("인증 정보 조회가 실패해도 메인 스펙 생성은 완료된다")
    void generateSpec_completesWhenCertificationFails() {
        AiProviderService aiProviderService = mock(AiProviderService.class);
        ScrapingService scrapingService = mock(ScrapingService.class);
        MemberService memberService = mock(MemberService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Executor executor = Runnable::run;

        GenerationServiceImpl service = new GenerationServiceImpl(
                aiProviderService,
                scrapingService,
                memberService,
                executor,
                eventPublisher
        );

        GenerateElectronicRequest request = new GenerateElectronicRequest();
        request.setModelName("AX40R3080WMD");
        request.setSpecExample("sample-spec");
        request.setProductNameExample("sample-name");

        Member member = Member.createForToken(10L, "user@test.com", Role.PLAN_30K);

        when(scrapingService.findG2bClassificationNumber(anyString())).thenReturn(Optional.of("23642147"));
        when(scrapingService.findCountryOfOrigin(anyString())).thenReturn(Optional.empty());

        CompletableFuture<CertificationResponse> certFailed = new CompletableFuture<>();
        certFailed.completeExceptionally(new TimeoutException("cert-timeout"));
        when(aiProviderService.fetchCertification(anyString())).thenReturn(certFailed);

        GenerateElectronicResponse mainSpec = new GenerateElectronicResponse();
        mainSpec.setModelName("AX40R3080WMD");
        mainSpec.setProductName("삼성 공기청정기");
        mainSpec.setSpecification("sample");
        when(aiProviderService.fetchMainSpec(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mainSpec));

        GenerateElectronicResponse result = service.generateSpec(request, member).join();

        assertThat(result.getModelName()).isEqualTo("AX40R3080WMD");
        assertThat(result.getG2bClassificationNumber()).isEqualTo("23642147");
        assertThat(result.getKatsCertificationNumber()).isNull();
        assertThat(result.getKcCertificationNumber()).isNull();

        verify(memberService).decrementCredit(10L);
        verify(memberService, never()).restoreCredit(anyLong());
    }
}

