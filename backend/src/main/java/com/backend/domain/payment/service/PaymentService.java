package com.backend.domain.payment.service;

import com.backend.domain.member.entity.Member;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // 결제 요청 전 DB에 주문 정보 저장
    public Payment requestPayment(Member member, Long amount) {
        String orderId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .member(member)
                .amount(amount)
                .orderId(orderId)
                .paymentKey(null)
                .status("READY")
                .build();

        return paymentRepository.save(payment);
    }
}