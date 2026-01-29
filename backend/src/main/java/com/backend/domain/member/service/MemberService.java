package com.backend.domain.member.service;

import com.backend.domain.member.dto.MemberInfoDto;
import com.backend.domain.member.dto.MemberResponseDto;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.global.auth.entity.MemberDetails;
import com.backend.global.auth.jwt.JwtProvider;
import com.backend.global.auth.refreshtoken.RefreshToken;
import com.backend.global.auth.refreshtoken.RefreshTokenService;
import com.backend.global.exception.AuthenticationException;
import com.backend.global.exception.InsufficientCreditException;
import com.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

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

        return jwtProvider.createAccessToken(email, member.getRole(),member.getMemberId());
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
        Long memberId = ((MemberDetails) authentication.getPrincipal()).member().getMemberId();

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

    /**
     * 크레딧 차감 (선차감 적용)
     * REQUIRES_NEW: 상위 트랜잭션과 무관하게 즉시 DB 반영
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrementCredit(Long memberId) {
        // 1. DB 레벨에서 원자적 차감 시도
        int updatedRows = memberRepository.decrementCreditIfPossible(memberId);

        // 2. 실패 시(크레딧 0) 예외 발생 -> 컨트롤러까지 전파되어 작업 중단
        if (updatedRows == 0) {
            // 현재 크레딧 조회를 위해 예외 메시지 생성 (선택사항)
            Member member = memberRepository.findById(memberId).orElseThrow();
            throw new InsufficientCreditException(member.getCredit());
        }

    }

    /**
     * 크레딧 복구 (작업 실패 시 환불)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreCredit(Long memberId) {
        memberRepository.incrementCredit(memberId);
    }

    @Transactional(readOnly = true)
    public MemberResponseDto getMemberInfo(Member member) {
        Member realMember = memberRepository.findById(member.getMemberId())
                .orElseThrow(() -> NotFoundException.entityNotFound("Member"));
        return new MemberResponseDto(realMember);
    }
}
