package com.backend.domain.member.entity;

import lombok.Getter;

@Getter
public enum Role {
    FREE_USER(2),      // 무료 사용자 (일일 2개)
    PLAN_30K(10),      // 3만원 플랜 (일일 10개)
    PLAN_50K(20),      // 5만원 플랜 (일일 20개)
    ADMIN(Integer.MAX_VALUE); // 관리자 (무제한)

    private final int dailyCreditLimit;

    Role(int dailyCreditLimit) {
        this.dailyCreditLimit = dailyCreditLimit;
    }
}