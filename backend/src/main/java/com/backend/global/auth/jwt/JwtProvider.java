package com.backend.global.auth.jwt;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.global.auth.entity.MemberDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j(topic = "JwtProvider")
@Component
public class JwtProvider {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String REFRESH_TOKEN_HEADER = "Refresh-token";

    private static final String BEARER_PREFIX = "Bearer ";

    public long JWT_TOKEN_EXPIRATION_TIME = 60 * 60 * 1000L * 5; // 5시간
    public long REFRESH_TOKEN_EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000L; // 1주일

    @Value("${jwt.token.secretKey}")
    private String SECRET_KEY;
    private Key key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String getTokenFromRequest(HttpServletRequest request, String headerName) {
        String bearerToken = request.getHeader(headerName);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public String createAccessToken(String email, Role role, Long memberId) {
        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_EXPIRATION_TIME))
                .claim("role", role)
                .claim("memberId", memberId)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String email, Role role, Long memberId) {
        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .claim("memberId", memberId)
                .setExpiration(new Date(System.currentTimeMillis() + (REFRESH_TOKEN_EXPIRATION_TIME)))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateToken(String token) {
        String tokenValue = resolveToken(token);
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(tokenValue)
                    .getBody();
        } catch (Exception e) {
            log.error("토큰 검증 실패: {}", e.getMessage());
            throw new JwtException("유효하지 않은 토큰입니다.");
        }
    }

    // [수정] 리플렉션 제거 및 정적 팩토리 메서드 사용
    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        String email = claims.getSubject();
        String roleStr = claims.get("role", String.class);
        Long memberId = claims.get("memberId", Long.class);

        Role role = Role.valueOf(roleStr);

        Member member = Member.createForToken(memberId, email, role);

        MemberDetails principal = new MemberDetails(member);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    public String getSubjectFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Claims getClaims(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public void addJwtToCookie(String token, HttpServletResponse res) {
        token = URLEncoder.encode(token, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        Cookie cookie = new Cookie(REFRESH_TOKEN_HEADER, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(7 * 24 * 3600);
        res.addCookie(cookie);
    }

    private String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}