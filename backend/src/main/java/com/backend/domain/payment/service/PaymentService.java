package com.backend.domain.payment.service;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;

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

    private void handleSuccessfulPayment(Payment payment, String paymentKey) {
        payment.completePayment(paymentKey);

        Member member = payment.getMember();
        Long amount = payment.getAmount();
        int durationDays = 30; // 모든 플랜이 30일 기준

        Role newRole;
        if (amount == 30000L) {
            newRole = Role.PLAN_30K;
        } else if (amount == 50000L) {
            newRole = Role.PLAN_50K;
        } else {
            // todo: 정의되지 않은 금액 처리(임시로 무료 사용자 유지)
            newRole = member.getRole(); // 기존 역할 유지
            durationDays = 0; // 기간 변경 없음
        }

        if (durationDays > 0) {
            member.updateMembership(newRole, durationDays);
        }

        // 변경된 Member, Payment 엔티티 저장
        memberRepository.save(member);
        paymentRepository.save(payment);
    }
}