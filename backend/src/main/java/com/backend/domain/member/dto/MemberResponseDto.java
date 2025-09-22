package com.backend.domain.member.dto;

import com.backend.domain.member.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class MemberResponseDto {
    private final String email;
    private final String name;
    private final String role;
    private final int credit;
    private final int dailyRequestCount;

    public MemberResponseDto(Member member) {
        this.email = member.getEmail();
        this.name = member.getName();
        this.role = member.getRole().name();
        this.credit = member.getCredit();
        this.dailyRequestCount = member.getDailyRequestCount();
    }
}
