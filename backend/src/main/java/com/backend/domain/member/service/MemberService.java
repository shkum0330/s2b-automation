package com.backend.domain.member.service;

import com.backend.domain.member.dto.MemberInfoDto;
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

    /**
     * Reissues an access token using a provided refresh token.
     *
     * Validates the provided refresh token (must be non-null and valid), extracts the subject email,
     * verifies the refresh token matches the stored refresh token for that email (the method strips a
     * "Bearer " prefix when comparing), and loads the corresponding Member. If verification succeeds,
     * returns a newly created access token for the member's email and role.
     *
     * @param refreshToken the refresh token string, expected in the form "Bearer <token>"
     * @return a newly created access token for the authenticated member
     * @throws AuthenticationException if the refresh token is null or fails validation
     * @throws NotFoundException if the stored refresh token or the Member for the token's email is not found
     */
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
     * Logs out a member by invalidating the provided refresh token.
     *
     * <p>Verifies the caller is an authenticated MemberDetails principal and that a non-null,
     * valid refresh token is supplied, then removes the stored refresh token associated with
     * the token's subject (email).</p>
     *
     * @param refreshToken the refresh token to invalidate (must be non-null and valid)
     * @throws AuthenticationException if the refresh token is null, the current principal is not a member,
     *                                 or the token is invalid/unacceptable
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
         * Updates and persists a member's basic profile information.
         *
         * The member's name and phone number are taken from the provided DTO, trimmed, set on the
         * given Member entity, and then saved. The provided profileImage parameter is accepted but
         * currently not used by this method.
         *
         * @param profileImage uploaded profile image (currently ignored)
         * @param dto contains the new name and phone values to apply
         * @param member the Member entity to update and persist
         */
    @Transactional
    public void insertMemberInfo(MultipartFile profileImage, MemberInfoDto dto, Member member) throws IOException {
        member.setName(dto.getName().trim());
        member.setPhone(dto.getPhone().trim());
        memberRepository.save(member);
    }

}
