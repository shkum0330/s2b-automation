package com.backend.domain.member.dto;

import com.backend.domain.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KakaoRegisterResultDto {
    private Member member;
    private boolean isNewMember;
}
