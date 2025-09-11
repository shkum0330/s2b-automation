package com.backend.member.domain;

import com.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id") // ❗️실제 DB 컬럼명을 'member_id'로 매핑
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private int credit = 0;

    @Column(nullable = false)
    private int dailyRequestCount = 0;

    private LocalDate lastRequestDate;

    @Builder
    public Member(String username, String email, String password, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // 잔여 횟수 차감 비즈니스 로직
    public void decrementRequestCount() {
        if (this.role == Role.PAID_USER) {
            if (this.credit <= 0) {
                throw new IllegalStateException("크레딧이 부족합니다.");
            }
            this.credit--;
        } else {
            if (this.lastRequestDate == null || !this.lastRequestDate.isEqual(LocalDate.now())) {
                this.dailyRequestCount = 0;
            }

            if (this.dailyRequestCount >= 2) {
                throw new IllegalStateException("일일 요청 횟수를 초과했습니다.");
            }
            this.dailyRequestCount++;
            this.lastRequestDate = LocalDate.now();
        }
    }
}