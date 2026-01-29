package com.backend.domain.member.controller;

import com.backend.domain.member.dto.MemberResponseDto;
import com.backend.domain.member.service.MemberService;
import com.backend.global.auth.entity.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<MemberResponseDto> getMyInfo(@AuthenticationPrincipal MemberDetails memberDetails) {
        MemberResponseDto memberInfo = memberService.getMemberInfo(memberDetails.member());
        return ResponseEntity.ok(memberInfo);
    }
}
