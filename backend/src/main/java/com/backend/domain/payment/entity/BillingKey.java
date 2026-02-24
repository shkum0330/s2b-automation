package com.backend.domain.payment.entity;

import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재시작/다중 인스턴스 환경에서도 결제 수단 키를 일관되게 조회하기 위한 영속 매핑 엔티티.
 */
@Entity
@Table(name = "billing_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseTimeEntity {

    @Id
    @Column(nullable = false, length = 120)
    private String customerKey;

    @Column(nullable = false, length = 200)
    private String billingKey;

    @Builder
    public BillingKey(String customerKey, String billingKey) {
        this.customerKey = customerKey;
        this.billingKey = billingKey;
    }

    public void updateBillingKey(String billingKey) {
        this.billingKey = billingKey;
    }
}
