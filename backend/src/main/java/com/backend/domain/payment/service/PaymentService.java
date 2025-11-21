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
    private static final int SUBSCRIPTION_DAYS = 30;

    @PostConstruct
    public void init() {
        String basicAuth = Base64.getEncoder().encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.tossWebClient = webClientBuilder
                .baseUrl(tossApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Payment requestPayment(Member member, Long amount, String orderName) {
        if (!isValidAmount(amount)) {
            throw new IllegalArgumentException("유효하지 않은 결제 금액입니다.");
        }

        String orderId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .member(member)
                .amount(amount)
                .orderName(orderName) // [추가]
                .orderId(orderId)
                .paymentKey(null) // 아직 승인 전이므로 null
                .status("READY")
                .build();

        return paymentRepository.save(payment);
    }

    public Mono<TossConfirmResponseDto> confirmPayment(String paymentKey, String orderId, Long amount) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("주문 정보를 찾을 수 없습니다. orderId=" + orderId));

        // 금액 검증
        if (!Objects.equals(payment.getAmount(), amount)) {
            payment.failPayment(); // 금액 불일치 시 결제 중단 처리
            return Mono.error(new IllegalArgumentException("주문 금액이 일치하지 않습니다."));
        }

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        // 승인 API 호출
        return tossWebClient.post()
                .uri("/v1/payments/confirm")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TossConfirmResponseDto.class)
                .doOnSuccess(response -> {
                    if (response != null && "DONE".equals(response.getStatus())) {
                        handleSuccessfulPayment(payment, response);
                    }
                })
                .doOnError(error -> {
                    log.error("결제 승인 실패: {}", error.getMessage());
                    // 필요하다면 여기서 결제 실패 상태로 업데이트
                });
    }

    private void handleSuccessfulPayment(Payment payment, TossConfirmResponseDto response) {
        // 결제 정보 업데이트
        payment.completePayment(response.getPaymentKey());

        // 멤버십 적용
        Member member = payment.getMember();
        Long amount = payment.getAmount();
        Role newRole = getRoleByAmount(amount);

        if (newRole != null) {
            member.updateMembership(newRole, SUBSCRIPTION_DAYS);
            memberRepository.save(member);
            log.info("결제 성공: 사용자(ID={}) 등급이 {}로 변경되었습니다.", member.getMemberId(), newRole);
        }
    }

    private Role getRoleByAmount(Long amount) {
        if (amount == 29900L) return Role.PLAN_30K;
        if (amount == 49900L) return Role.PLAN_50K;
        if (amount == 100000L) return Role.PLAN_100K;
        return null;
    }

    private boolean isValidAmount(Long amount) {
        return amount == 29900L || amount == 49900L || amount == 100000L;
    }
}