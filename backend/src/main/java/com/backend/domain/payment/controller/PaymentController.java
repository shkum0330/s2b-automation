package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.payment.dto.PaymentRequestDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // 1. 주문 생성 API
    @PostMapping("/request")
    public ResponseEntity<?> requestPayment(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody PaymentRequestDto requestDto
    ) {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            Member member = memberDetails.member();
            Payment payment = paymentService.requestPayment(member, requestDto.getAmount(), requestDto.getOrderName());

            return ResponseEntity.ok(Map.of(
                    "orderId", payment.getOrderId(),
                    "amount", payment.getAmount(),
                    "orderName", payment.getOrderName(),
                    "customerEmail", member.getEmail(), // 샘플 프로젝트처럼 이메일, 이름도 전달 가능
                    "customerName", member.getName() != null ? member.getName() : "비회원"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 2. 결제 승인 API (Toss에서 리다이렉트되어 들어옴)
    @GetMapping("/success")
    public Mono<ResponseEntity<Map<String, Object>>> successPayment(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount
    ) {
        return paymentService.confirmPayment(paymentKey, orderId, amount)
                .map(responseDto -> ResponseEntity.ok(Map.of(
                        "message", "결제가 성공적으로 완료되었습니다.",
                        "orderId", responseDto.getOrderId(),
                        "totalAmount", responseDto.getTotalAmount()
                )))
                .onErrorResume(Exception.class, e -> {
                    log.error("결제 승인 중 오류 발생", e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "결제 승인 실패: " + e.getMessage())));
                });
    }

    // 3. 결제 실패 API
    @GetMapping("/fail")
    public ResponseEntity<?> failPayment(
            @RequestParam String code,
            @RequestParam String message,
            @RequestParam String orderId
    ) {
        log.warn("결제 실패: [Code: {}] [Message: {}] [OrderId: {}]", code, message, orderId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "결제에 실패했습니다: " + message,
                "code", code,
                "orderId", orderId
        ));
    }
}