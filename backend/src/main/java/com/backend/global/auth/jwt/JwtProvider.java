package com.backend.global.auth.jwt;

import com.backend.domain.member.entity.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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


    public String createAccessToken(String email, Role role) {
        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_EXPIRATION_TIME))
                .claim("role", role)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String email, Role role) {

        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .claim("role", role)
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
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 형식의 JWT 토큰입니다. Token: {}", tokenValue);
            throw new JwtException("지원되지 않는 토큰 형식입니다.");
        } catch (MalformedJwtException e) {
            log.error("잘못된 형식의 JWT 토큰입니다. Token: {}", tokenValue);
            throw new JwtException("잘못된 토큰 형식입니다.");
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다. Token: {}", tokenValue);
            throw new JwtException("토큰이 만료되었습니다.");
        } catch (SignatureException e) {
            log.error("JWT 서명이 유효하지 않습니다. Token: {}", tokenValue);
            throw new JwtException("서명이 유효하지 않습니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 null이거나 비어있습니다. Token: {}", tokenValue);
            throw new JwtException("토큰 값이 비어있습니다.");
        }
    }

    public String getSubjectFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
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
        Cookie cookie = new Cookie(JwtProvider.REFRESH_TOKEN_HEADER, token);

        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", "None");

        int maxAgeInSeconds = 3600; // 1시간
        cookie.setMaxAge(7 * 24 * maxAgeInSeconds);
        res.addCookie(cookie);
    }

    private String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken; // "Bearer "가 없으면 원본 반환
    }
}
