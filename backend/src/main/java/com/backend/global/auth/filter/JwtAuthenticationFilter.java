package com.backend.global.auth.filter;

import com.backend.global.auth.jwt.JwtProvider;
import com.backend.global.exception.AuthenticationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j(topic = "JwtAuthenticationFilter")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService detailsService;

    // 필터가 예외적으로 동작하지 않아야 할 URI 목록
    private static final List<String> EXCLUDE_URIS = List.of(
            "/api/v1/auth/callback/kakao",
            "/api/v1/auth/token",
            "/actuator/health"
    );

    /**
     * 이 필터를 건너뛸지 여부를 결정합니다.
     * EXCLUDE_URIS에 포함된 경로는 이 필터의 로직을 실행하지 않습니다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS 메서드는 항상 통과
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return EXCLUDE_URIS.stream().anyMatch(uri -> request.getRequestURI().startsWith(uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 헤더에서 Access Token 추출
        String accessToken = jwtProvider.getTokenFromRequest(request, JwtProvider.AUTHORIZATION_HEADER);

        // 2. 토큰이 없는 경우, 다음 필터로 위임
        if (!StringUtils.hasText(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 3. 토큰이 유효한 경우
            jwtProvider.validateToken(accessToken); // 유효성 검증

            // 4. 사용자 정보로 Authentication 객체 생성 및 SecurityContext에 등록
            String username = jwtProvider.getSubjectFromToken(accessToken);
            UserDetails userDetails = detailsService.loadUserByUsername(username);

            // 역할(Role) 검증은 SecurityConfig의 .hasRole()에서 처리하는 것이 더 일반적입니다.
            //    필요하다면 아래 로직을 유지할 수 있습니다.
            // validateUserRole(userDetails, jwtProvider.getRoleFromToken(accessToken));

            log.info("👮‍♂️ 인증 디버깅 - 사용자: {}, 권한: {}", username, userDetails.getAuthorities());
            setAuthentication(userDetails);

        } catch (Exception e) {
            // 5. 토큰이 유효하지 않은 경우, SecurityContext를 비우고 예외를 전파하지 않음
            //    -> 뒤따르는 필터(특히 ExceptionTranslationFilter)가 처리하도록 함
            SecurityContextHolder.clearContext();
            log.warn("JWT 인증 실패: {}, 요청 URI: {}", e.getMessage(), request.getRequestURI());
            // 여기서 직접 response.setStatus()를 설정하기보다,
            // Spring Security의 ExceptionTranslationFilter가 처리하도록 두는 것이 좋다
        }

        // 6. 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(UserDetails userDetails) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
