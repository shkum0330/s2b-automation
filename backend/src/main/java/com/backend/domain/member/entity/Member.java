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
    private Long member_id;

    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    private String name;

    @Setter
    private String phone;

    @Enumerated(EnumType.STRING)
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

    @Builder
    public Member(String name, String email, String provider, String providerId, Role role) {
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    // 잔여 횟수 차감 비즈니스 로직
    public void decrementRequestCount() {
        if (this.credit <= 0) {
            throw new IllegalStateException("크레딧이 부족합니다.");
        }
        this.credit--;
    }
}