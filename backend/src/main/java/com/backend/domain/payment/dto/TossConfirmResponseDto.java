package com.backend.domain.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossConfirmResponseDto {
    private String paymentKey;
    private String orderId;
    private String status;
    private String approvedAt;
    private Long totalAmount;
    // todo: 필요한 다른 응답 필드들 추가
}