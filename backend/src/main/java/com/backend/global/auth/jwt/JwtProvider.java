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

    /**
     * Initializes the cryptographic signing key used for JWT operations.
     *
     * Decodes the configured base64-encoded SECRET_KEY and builds an HMAC-SHA key
     * suitable for signing and verifying JWTs (HS256). Executed once after the
     * component is constructed (@PostConstruct).
     *
     * Preconditions: SECRET_KEY must be a valid base64-encoded secret of adequate
     * length for the HMAC-SHA algorithm.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extracts a Bearer token from the specified HTTP header.
     *
     * If the header value is present and starts with "Bearer ", the prefix is removed and the remaining token is returned.
     *
     * @param request    the HTTP request to read the header from
     * @param headerName the name of the header that may contain a Bearer token (e.g. "Authorization")
     * @return the token string without the "Bearer " prefix, or {@code null} if the header is missing or not Bearer-formatted
     */
    public String getTokenFromRequest(HttpServletRequest request, String headerName) {
        String bearerToken = request.getHeader(headerName);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }


    /**
     * Creates a signed access JWT for the given user and role.
     *
     * The token's subject is the provided email, it contains a "role" claim, and is
     * issued with the configured access-token expiration. The returned string is
     * prefixed with "Bearer ".
     *
     * @param email the user's email to set as the JWT subject
     * @param role  the user's role added to the token as the "role" claim
     * @return a Bearer-prefixed JWT access token (signed with the configured HS256 key)
     */
    public String createAccessToken(String email, Role role) {
        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_EXPIRATION_TIME))
                .claim("role", role)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Creates a signed refresh JWT and returns it prefixed with "Bearer ".
     *
     * The token's subject is set to the provided email and it contains a "role" claim.
     * Its expiration is determined by REFRESH_TOKEN_EXPIRATION_TIME.
     *
     * @param email the subject (user identifier) to embed in the token
     * @param role  the user's role stored as the "role" claim
     * @return a complete refresh token string prefixed with "Bearer "
     */
    public String createRefreshToken(String email, Role role) {

        return BEARER_PREFIX + Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + (REFRESH_TOKEN_EXPIRATION_TIME)))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates the given JWT string and returns its parsed claims.
     *
     * Accepts either a raw JWT or a `Bearer `-prefixed value; the prefix will be stripped before validation.
     *
     * @param token the JWT value (may include the "Bearer " prefix)
     * @return the token's Claims if validation succeeds
     * @throws JwtException if the token is unsupported, malformed, expired, has an invalid signature, or is null/empty
     */
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

    /**
     * Extracts and returns the JWT subject (typically the user's identifier/email).
     *
     * The provided token may include the "Bearer " prefix; it will be handled by the underlying claim parser.
     *
     * @param token the JWT string (optionally prefixed with "Bearer ")
     * @return the subject claim from the token (e.g., user's email)
     */
    public String getSubjectFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * Extracts the "role" claim from the provided JWT.
     *
     * @param token the JWT string (may include the "Bearer " prefix)
     * @return the value of the "role" claim, or {@code null} if the claim is not present
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Parses the JWT (optionally prefixed with "Bearer ") and returns its claims.
     *
     * @param token the JWT string to parse; may include the leading "Bearer " prefix
     * @return the token's Claims (the parsed JWT body)
     */
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

    /**
     * Encodes the given JWT and adds it to the HTTP response as a refresh-token cookie.
     *
     * The cookie is named using JwtProvider.REFRESH_TOKEN_HEADER, stores the URL-encoded token
     * (spaces encoded as `%20`), is scoped to path "/", marked HttpOnly and Secure, uses
     * SameSite=None, and has a max age of 7 days.
     *
     * @param token the JWT to store in the cookie
     */
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

    /**
     * Strips the "Bearer " prefix from a token value if present.
     *
     * @param bearerToken the raw header value that may start with "Bearer "; may be null
     * @return the token without the "Bearer " prefix when present, or the original value (possibly null) otherwise
     */
    private String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken; // "Bearer "가 없으면 원본 반환
    }
}
