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

    /**
     * 1. 결제 요청 (주문 생성)
     */
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
            Payment payment = paymentService.requestPayment(member, requestDto.getAmount());

            return ResponseEntity.ok(Map.of(
                    "orderId", payment.getOrderId(),
                    "amount", payment.getAmount()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 2. 결제 승인 (Toss 리다이렉트 URL)
     */
    @GetMapping("/success")
    public Mono<ResponseEntity<Map<String, Object>>> successPayment(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount
    ) {
        return paymentService.confirmPayment(paymentKey, orderId, amount)
                .map(responseDto -> {
                    ResponseEntity<Map<String, Object>> successResponse = ResponseEntity.ok(Map.of(
                            "message", "결제가 성공적으로 완료되었습니다.",
                            "orderId", responseDto.getOrderId(),
                            "totalAmount", responseDto.getTotalAmount()
                    ));
                    return successResponse;
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.warn("결제 승인 실패 (IllegalArgumentException): {}", e.getMessage());
                    ResponseEntity<Map<String, Object>> errorResponse = ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("message", e.getMessage()));
                    return Mono.just(errorResponse);
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("결제 승인 중 심각한 오류 발생", e);
                    ResponseEntity<Map<String, Object>> errorResponse = ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "결제 승인 중 오류가 발생했습니다."));
                    return Mono.just(errorResponse);
                });
    }

    /**
     * 3. 결제 실패 (Toss 리다이렉트 URL)
     */
    @GetMapping("/fail")
    public ResponseEntity<?> failPayment(
            @RequestParam String code,
            @RequestParam String message,
            @RequestParam String orderId
    ) {
        log.warn("결제 실패: [Code: {}] [Message: {}] [OrderId: {}]", code, message, orderId);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "결제에 실패했습니다: " + message,
                "orderId", orderId
        ));
    }
}