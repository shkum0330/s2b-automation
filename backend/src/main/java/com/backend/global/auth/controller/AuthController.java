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
     * Handles the Kakao OAuth callback and completes user login.
     *
     * Exchanges the provided Kakao authorization `code` for user credentials via KakaoService,
     * sets any required cookies/headers on the provided HttpServletResponse, and returns a
     * LoginResponseDto in a 200 OK response.
     *
     * @param code the OAuth authorization code returned by Kakao
     * @param response servlet response used to set cookies or headers required for the login session
     * @return ResponseEntity containing a LoginResponseDto with login result details
     * @throws IOException if an I/O error occurs while writing to the response
     */
    @GetMapping("/callback/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestParam(name = "code") String code, HttpServletResponse response) throws IOException {
        LoginResponseDto dto = kakaoService.kakaoLogin(code, response);
        return ResponseEntity.ok(dto);
    }

    /**
     * Refreshes the access token using a refresh token from the "Refresh-token" cookie.
     *
     * <p>Delegates to MemberService to issue a new access token, then returns a 200 OK response
     * with the new token placed in the response Authorization header (JwtProvider.AUTHORIZATION_HEADER)
     * and a JSON body containing a confirmation message.</p>
     *
     * @param refreshToken the refresh token value read from the "Refresh-token" cookie
     * @return a ResponseEntity with the Authorization header set to the newly issued access token
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
     * Logs out the current user by invalidating server-side session state and removing the refresh-token cookie.
     *
     * Calls MemberService.logout with the provided refresh token, then adds a cleared (empty, Max-Age=0) cookie
     * named by JwtProvider.REFRESH_TOKEN_HEADER with Path=/, HttpOnly, Secure and SameSite=None to the response.
     *
     * @param refreshToken the value of the "Refresh-token" cookie supplied by the client
     * @param response the HttpServletResponse to which the cleared refresh-token cookie will be added
     * @return ResponseEntity with HTTP 200 OK containing the HttpServletResponse
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
