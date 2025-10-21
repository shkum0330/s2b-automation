package com.backend.domain.payment.entity;

import com.backend.domain.member.entity.Member;
import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false, unique = true)
    private String paymentKey; // 결제의 고유 키 (승인 시 저장)

    @Column(nullable = false, unique = true)
    private String orderId; // 주문 ID

    @Column(nullable = false)
    private Long amount; // 결제 금액

    @Column(nullable = false)
    private String status; // 결제 상태 (READY, DONE, CANCELED 등)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member; // 결제를 요청한 회원

    @Builder
    public Payment(String paymentKey, String orderId, Long amount, String status, Member member) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.member = member;
    }

    public void completePayment(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = "DONE";
    }
}