package com.backend.domain.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class LoginResponseDto {
    private Long memberId;
    private String email;
    private boolean isNewMember;
    private boolean hasMemberInfo;
}
