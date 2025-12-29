package com.backend.global.auth.controller;

import com.backend.domain.member.dto.LoginResponseDto;
import com.backend.domain.member.service.KakaoService;
import com.backend.domain.member.service.MemberService;
import com.backend.global.auth.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final KakaoService kakaoService;
    private final MemberService memberService;

    /**
     * 카카오 로그인 처리 컨트롤러
     */
    @GetMapping("/callback/kakao")
    public ResponseEntity<?> kakaoLogin(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "redirectUri", required = false) String redirectUri,
            HttpServletResponse response) throws IOException {

        // todo: 기본 리다이렉트 uri 설정
        String uri = (redirectUri != null && !redirectUri.isBlank()) ? redirectUri : "http://localhost:8989";

        LoginResponseDto dto = kakaoService.kakaoLogin(code, uri, response);
        return ResponseEntity.ok(dto);
    }

    /**
     * access token 재발급 요청 처리 컨트롤러
     */
    @PostMapping("/token")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "Refresh-token") String refreshToken) {
        String newAccessToken = memberService.refreshAccessToken(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.set(JwtProvider.AUTHORIZATION_HEADER, newAccessToken);

        return ResponseEntity.ok()
                .headers(headers)
                .body(Map.of("message", "Access Token이 재발급되었습니다."));
    }

    /**
     * 로그아웃 처리 컨트롤러
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "Refresh-token") String refreshToken, HttpServletResponse response) {
        memberService.logout(refreshToken);
        Cookie cookie = new Cookie(JwtProvider.REFRESH_TOKEN_HEADER, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", "None");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(response);
    }
}
