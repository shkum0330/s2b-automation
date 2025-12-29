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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrementCredit(Long memberId) {
        LocalDate today = LocalDate.now();

        // 1. 회원 정보를 읽어옴
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("ID에 해당하는 멤버를 찾을 수 없습니다: " + memberId));

        // 2. 구독 만료일 체크 및 갱신
        if (member.getPlanExpiresAt() != null && member.getPlanExpiresAt().isBefore(today)) {
            member.setRole(Role.FREE_USER);
            member.setPlanExpiresAt(null);
            log.info("사용자(ID: {})의 구독이 만료되어 FREE_USER로 변경되었습니다.", memberId);
        }

        // 3. 일일 사용량 초기화 체크
        if (member.getLastRequestDate() == null || !member.getLastRequestDate().equals(today)) {
            member.resetDailyCount(today);
            // resetDailyCount는 dailyRequestCount를 0으로 설정하므로, DB에 즉시 반영
            memberRepository.saveAndFlush(member);
            log.info("사용자(ID: {})의 일일 사용량이 초기화되었습니다.", memberId);
        }

        // 4. 현재 등급의 일일 한도 확인
        int dailyLimit = member.getRole().getDailyCreditLimit();

        // 5. 한도 내에서 일일 사용량 원자적으로 증가
        log.info("사용자(ID: {}) 크레딧 사용 시도. (현재 사용량: {}, 한도: {})", memberId, member.getDailyRequestCount(), dailyLimit);

        int updated = memberRepository.incrementDailyCountIfPossible(memberId, dailyLimit);

        if (updated == 0) {
            throw new IllegalStateException("일일 크레딧 한도를 초과했습니다.");
        }

        log.info("사용자(ID: {}) 크레딧 사용 성공. (갱신 후 사용량: {}/{})", memberId, member.getDailyRequestCount() + 1, dailyLimit);
    }

    @Transactional(readOnly = true)
    public MemberResponseDto getMemberInfo(Member member) {
        return new MemberResponseDto(member);
    }
}
