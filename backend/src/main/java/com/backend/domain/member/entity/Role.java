package com.backend.domain.member.entity;

import lombok.Getter;

@Getter
public enum Role {
    FREE_USER(5),      // 무료 사용자 (일일 3개)
    PLAN_30K(10),      // 29900원 플랜 (일일 10개)
    PLAN_50K(20),      // 49900원 플랜 (일일 20개)
    PLAN_100K(50),     // 99900원 플랜 (일일 50개)
    ADMIN(Integer.MAX_VALUE); // 관리자 (무제한)

    private final int dailyCreditLimit;

    Role(int dailyCreditLimit) {
        this.dailyCreditLimit = dailyCreditLimit;
    }
}