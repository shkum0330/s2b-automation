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

    // ❗️ 필터가 예외적으로 동작하지 않아야 할 URI 목록
    private static final List<String> EXCLUDE_URIS = List.of(
            "/api/v1/auth/callback/kakao",
            "/api/v1/auth/token",
            "/actuator/health"
    );

    /**
     * Determine whether this filter should be skipped for the given request.
     *
     * Returns true for HTTP OPTIONS requests or when the request URI starts with any path listed in EXCLUDE_URIS.
     *
     * @return true if the filter should not run for the request; false otherwise
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS 메서드는 항상 통과
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return EXCLUDE_URIS.stream().anyMatch(uri -> request.getRequestURI().startsWith(uri));
    }

    /**
     * Authenticates the incoming HTTP request using a JWT from the Authorization header and populates
     * the SecurityContext with a UsernamePasswordAuthenticationToken when validation succeeds.
     *
     * <p>Behavior:
     * - Extracts the access token from the request Authorization header via JwtProvider.
     * - If no token is present, the request is delegated to the next filter without setting authentication.
     * - If a token is present, validates it, extracts the subject (username), loads UserDetails and sets
     *   the Authentication into the SecurityContext.
     * - If validation or parsing fails, clears the SecurityContext and does not propagate the exception
     *   (allows downstream Spring Security filters, e.g. ExceptionTranslationFilter, to handle it).
     * - Always forwards the request to the next filter in the chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 헤더에서 Access Token 추출
        String accessToken = jwtProvider.getTokenFromRequest(request, JwtProvider.AUTHORIZATION_HEADER);
        log.info("accessToken: {}", accessToken);
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

    /**
     * Populate the SecurityContext with an Authentication built from the given user details.
     *
     * Creates a UsernamePasswordAuthenticationToken using the provided UserDetails (no credentials)
     * and the user's granted authorities, then sets it as the current Authentication in the
     * SecurityContext.
     *
     * @param userDetails the authenticated user's details whose principal and authorities will be used
     */
    private void setAuthentication(UserDetails userDetails) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
