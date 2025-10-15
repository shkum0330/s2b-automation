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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
        Long memberId = ((MemberDetails) authentication.getPrincipal()).member().getMember_id();

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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("ID에 해당하는 멤버를 찾을 수 없습니다: " + memberId));

        // 영속 상태의 엔티티를 수정하면 더티 체킹으로 인해 트랜잭션 종료 시 자동으로 DB에 반영됨
        log.info("요청 전 크레딧: {}",member.getCredit());
        int updated = memberRepository.decrementCreditIfPossible(memberId);
        if (updated == 0) {
            throw new IllegalStateException("크레딧이 부족합니다.");
        }
        log.info("요청 후 크레딧: {}",member.getCredit());
    }

    @Transactional(readOnly = true)
    public MemberResponseDto getMemberInfo(Member member) {
        return new MemberResponseDto(member);
    }
}
