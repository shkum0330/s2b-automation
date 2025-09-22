package com.backend.domain.member.service;

import com.backend.domain.member.dto.MemberInfoDto;
import com.backend.domain.member.dto.MemberResponseDto;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.global.auth.entity.MemberDetails;
import com.backend.global.auth.jwt.JwtProvider;
import com.backend.global.auth.refreshtoken.RefreshToken;
import com.backend.global.auth.refreshtoken.RefreshTokenService;
import com.backend.global.exception.AuthenticationException;
import com.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "MemberService")
public class MemberService {
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    // refresh token 사용하여 access token 재발급
    @Transactional
    public String refreshAccessToken(String refreshToken) {
        if (refreshToken == null) {
            throw AuthenticationException.noRefreshToken();
        }

        jwtProvider.validateToken(refreshToken);
        String email = jwtProvider.getSubjectFromToken(refreshToken);

        RefreshToken storedRefreshToken = refreshTokenService.getRefreshToken(email)
                .orElseThrow(() -> NotFoundException.entityNotFound("Refresh Token"));

        if (!refreshTokenService.validateRefreshToken(email, refreshToken.substring(7))) {
            refreshTokenService.removeRefreshToken(storedRefreshToken);
            throw AuthenticationException.unauthenticatedToken(refreshToken);
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> NotFoundException.entityNotFound("멤버"));

        return jwtProvider.createAccessToken(email, member.getRole());
    }

    /**
     * refresh token의 Max age를 0으로 만들어 로그아웃 시키는 메서드
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            throw AuthenticationException.noRefreshToken();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof MemberDetails)) {
            throw new AuthenticationException("잘못된 인증 정보입니다.");
        }
        Long memberId = ((MemberDetails) authentication.getPrincipal()).member().getId();

        jwtProvider.validateToken(refreshToken);
        refreshTokenService.removeRefreshTokenByKeyEmail(jwtProvider.getSubjectFromToken(refreshToken));

    }

    /**
     * 사용자 정보를 등록하는 서비스 메서드
     */
    @Transactional
    public void insertMemberInfo(MultipartFile profileImage, MemberInfoDto dto, Member member) throws IOException {
        member.setName(dto.getName().trim());
        member.setPhone(dto.getPhone().trim());
        memberRepository.save(member);
    }

    @Transactional
    public void decrementCredit(Member member) {
        member.decrementRequestCount();
    }

    @Transactional(readOnly = true)
    public MemberResponseDto getMemberInfo(Member member) {
        return new MemberResponseDto(member);
    }
}
