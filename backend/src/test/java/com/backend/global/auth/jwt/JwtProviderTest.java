package com.backend.global.auth.jwt;

import com.backend.domain.member.entity.Role;
import com.backend.global.auth.entity.MemberDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 토큰 생성 및 유효성 검증 로직이 정상 작동하는지 테스트
 * DB 조회 없이 토큰의 Claims(memberId, Role)만으로 Authentication 객체가 올바르게 생성되는지 검증
 */
@SpringBootTest
class JwtProviderTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("토큰을 생성하고 유효성을 검증할 수 있다")
    void tokenCreateAndValidateTest() {
        // given
        String email = "test@example.com";
        Role role = Role.FREE_USER;
        Long memberId = 100L;

        // when
        String accessToken = jwtProvider.createAccessToken(email, role, memberId);

        // then
        assertThat(accessToken).isNotNull();
        assertThat(jwtProvider.validateToken(accessToken)).isNotNull();
    }

    @Test
    @DisplayName("토큰만으로 인증 객체를 생성할 수 있다")
    void getAuthenticationTest() {
        // given
        String email = "admin@test.com";
        Role role = Role.ADMIN;
        Long memberId = 999L;
        String token = jwtProvider.createAccessToken(email, role, memberId);

        // when
        Authentication authentication = jwtProvider.getAuthentication(token);

        // then
        assertThat(authentication).isNotNull();

        MemberDetails principal = (MemberDetails) authentication.getPrincipal();
        assertThat(principal.getUsername()).isEqualTo(email);
        assertThat(principal.member().getMemberId()).isEqualTo(memberId);
        assertThat(principal.member().getRole()).isEqualTo(role);
    }
}

