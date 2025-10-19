package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // 결제 요청
    @PostMapping("/request")
    public ResponseEntity<Payment> requestPayment(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody Long amount // 실제로는 amount 대신 상품 ID 등을 받아서 처리
    ) {
        Member member = memberDetails.member();
        Payment payment = paymentService.requestPayment(member, amount);
        return ResponseEntity.ok(payment);
    }

    //  결제 승인
    @GetMapping("/success")
    public ResponseEntity<?> successPayment(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount
    ) {
        // todo" 여기에 토스페이먼츠 결제 승인 API를 호출하고, DB의 주문 정보와 대조하여 검증하는 로직이 들어가야 함

        return ResponseEntity.ok().build();
    }

    // 결제 실패
    @GetMapping("/fail")
    public ResponseEntity<?> failPayment(
            @RequestParam String code,
            @RequestParam String message,
            @RequestParam String orderId
    ) {
        return ResponseEntity.status(400).body(Map.of("message", message, "orderId", orderId));
    }
}