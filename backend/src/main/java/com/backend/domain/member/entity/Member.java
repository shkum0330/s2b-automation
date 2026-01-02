package com.backend.domain.member.entity;

import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long memberId;

    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    private String name;

    @Setter
    private String phone;

    @Enumerated(EnumType.STRING)
    @Setter // 멤버십 변경을 위해 Setter 추가
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    private String provider;

    @Column(length = 50, unique = true)
    private String providerId;

    @Setter
    @Column(nullable = false)
    private int credit = 0;

    @Column(nullable = false)
    private int dailyRequestCount = 0;

    private LocalDate lastRequestDate;

    @Setter
    private LocalDate planExpiresAt; // 구독 만료일

    @Builder
    public Member(String name, String email, String provider, String providerId, Role role) {
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    // 새로운 멤버십 플랜으로 갱신
    public void updateMembership(Role newRole, int durationDays) {
        this.role = newRole;
        this.planExpiresAt = LocalDate.now().plusDays(durationDays);
        this.resetDailyCount(LocalDate.now());
    }

    // 일일 사용량 초기화
    public void resetDailyCount(LocalDate date) {
        this.dailyRequestCount = 0;
        this.lastRequestDate = date;
    }

    // 메모리 상의 객체 상태를 DB와 맞추기 위한 메서드
    public void decrementRequestCount() {
        if (this.credit <= 0) {
            // 이미 DB에서 검증했지만, 객체 상태 무결성을 위해 한 번 더 체크
            throw new IllegalStateException("크레딧이 부족합니다.");
        }
        this.credit--;
    }

}