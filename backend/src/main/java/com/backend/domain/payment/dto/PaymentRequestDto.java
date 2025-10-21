package com.backend.domain.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequestDto {
    private Long amount;
    // todo: 필요시 orderName 등 추가
}
