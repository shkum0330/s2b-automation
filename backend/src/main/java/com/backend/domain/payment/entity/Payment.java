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

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String orderName; // 주문명 (예: "30일 10개 플랜")

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String status; // READY, DONE, CANCELED, ABORTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public Payment(String paymentKey, String orderId, String orderName, Long amount, String status, Member member) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.orderName = orderName;
        this.amount = amount;
        this.status = status;
        this.member = member;
    }

    public void completePayment(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = "DONE";
    }

    public void failPayment() {
        this.status = "ABORTED";
    }
}