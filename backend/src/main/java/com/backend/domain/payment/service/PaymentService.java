package com.backend.domain.payment.service;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.domain.payment.dto.TossConfirmResponseDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.repository.PaymentRepository;
import com.backend.global.exception.NotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Value("${toss.api.url}")
    private String tossApiUrl;

    private WebClient tossWebClient;
    private static final int SUBSCRIPTION_DAYS = 30; // 구독 일수

    @PostConstruct
    public void init() {
        String basicAuth = Base64.getEncoder().encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        this.tossWebClient = webClientBuilder
                .baseUrl(tossApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 1. 결제 요청 (DB에 READY 상태로 저장)
     */
    public Payment requestPayment(Member member, Long amount) {
        if (!isValidAmount(amount)) {
            throw new IllegalArgumentException("유효하지 않은 결제 금액입니다.");
        }

        String orderId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .member(member)
                .amount(amount)
                .orderId(orderId)
                .paymentKey(null) // 승인 전
                .status("READY")
                .build();
        return paymentRepository.save(payment);
    }

    /**
     * 2. 결제 승인 (POST /v1/payments/confirm)
     */
    public Mono<TossConfirmResponseDto> confirmPayment(String paymentKey, String orderId, Long amount) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("주문 정보를 찾을 수 없습니다. (orderId: " + orderId + ")"));

        if (!Objects.equals(payment.getAmount(), amount)) {
            return Mono.error(new IllegalArgumentException("주문 금액이 일치하지 않습니다."));
        }

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        return tossWebClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", orderId) // 멱등성 키 추가
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TossConfirmResponseDto.class)
                .doOnSuccess(response -> {
                    if ("DONE".equals(response.getStatus())) {
                        handleSuccessfulPayment(payment, response);
                    } else {
                        log.warn("결제 승인은 되었으나 상태가 DONE이 아님: {}", response.getStatus());
                    }
                })
                .doOnError(error -> {
                    log.error("결제 승인 API 호출 실패: {}", error.getMessage());
                });
    }

    /**
     * 3. 결제 성공 후속 처리 (DB 업데이트 및 멤버십 적용)
     */
    private void handleSuccessfulPayment(Payment payment, TossConfirmResponseDto response) {

        payment.completePayment(response.getPaymentKey());

        Member member = payment.getMember();
        Long amount = payment.getAmount();

        Role newRole;
        if (amount == 29900L) {
            newRole = Role.PLAN_30K;
        } else if (amount == 49900L) {
            newRole = Role.PLAN_50K;
        } else if (amount == 99000L) {
            newRole = Role.PLAN_100K;
        } else {
            log.warn("handleSuccessfulPayment: 유효하지 않은 금액 {}이 승인 처리되었습니다.", amount);
            return;
        }

        member.updateMembership(newRole, SUBSCRIPTION_DAYS);
        memberRepository.save(member);
    }

    // 유효한 플랜 금액인지 확인하는 헬퍼 메서드
    private boolean isValidAmount(Long amount) {
        return amount == 29900L || amount == 49900L || amount == 100000L;
    }
}