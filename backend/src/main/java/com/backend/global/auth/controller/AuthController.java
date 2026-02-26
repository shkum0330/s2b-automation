package com.backend.global.auth.controller;

import com.backend.domain.member.dto.LoginResponseDto;
import com.backend.domain.member.service.KakaoService;
import com.backend.domain.member.service.MemberService;
import com.backend.global.auth.jwt.JwtProvider;
import com.backend.global.auth.service.OAuthStateService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final KakaoService kakaoService;
    private final MemberService memberService;
    private final OAuthStateService oauthStateService;

    @Value("${kakao.redirect-uri}")
    private String defaultRedirectUri;

    @Value("${kakao.allowed-redirect-uris:}")
    private String allowedRedirectUrisRaw;

    @GetMapping("/state")
    public ResponseEntity<Map<String, String>> issueOAuthState() {
        return ResponseEntity.ok(Map.of("state", oauthStateService.issueState()));
    }

    @GetMapping("/callback/kakao")
    public ResponseEntity<?> kakaoLogin(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "redirectUri", required = false) String redirectUri,
            HttpServletResponse response
    ) throws IOException {
        if (!oauthStateService.validateAndConsume(state)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "유효하지 않은 로그인 요청입니다."));
        }

        String resolvedRedirectUri = resolveRedirectUri(redirectUri);
        if (resolvedRedirectUri == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "허용되지 않은 redirectUri입니다."));
        }

        LoginResponseDto dto = kakaoService.kakaoLogin(code, resolvedRedirectUri, response);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/token")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "Refresh-token") String refreshToken) {
        String newAccessToken = memberService.refreshAccessToken(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.set(JwtProvider.AUTHORIZATION_HEADER, newAccessToken);

        return ResponseEntity.ok()
                .headers(headers)
                .body(Map.of("message", "Access Token이 재발급되었습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "Refresh-token") String refreshToken,
            HttpServletResponse response
    ) {
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

    private String resolveRedirectUri(String requestedRedirectUri) {
        String target = StringUtils.hasText(requestedRedirectUri) ? requestedRedirectUri : defaultRedirectUri;
        return isAllowedRedirectUri(target) ? target : null;
    }

    private boolean isAllowedRedirectUri(String target) {
        if (!StringUtils.hasText(target)) {
            return false;
        }

        Set<String> allowList = new HashSet<>();
        if (StringUtils.hasText(defaultRedirectUri)) {
            allowList.add(defaultRedirectUri);
        }

        if (StringUtils.hasText(allowedRedirectUrisRaw)) {
            Arrays.stream(allowedRedirectUrisRaw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(allowList::add);
        }

        return allowList.contains(target);
    }
}
