package com.backend.global.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.log.LogMessage;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j(topic = "AnonymousAuthenticationFilter")
public class AnonymousAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Ensures the current request has an Authentication in the SecurityContext and continues the filter chain.
     *
     * <p>If the SecurityContext has no Authentication, an anonymous Authentication is created and installed
     * on a new SecurityContext before proceeding. If an Authentication already exists, the SecurityContext
     * is left unchanged. The method always delegates to the provided FilterChain.</p>
     *
     * @param req the current HTTP request
     * @param res the current HTTP response
     * @param filterChain the filter chain to continue processing the request
     * @throws ServletException if an error occurs during filtering
     * @throws IOException if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Authentication authentication = createAuthentication((HttpServletRequest) req);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace(LogMessage.of(() -> "Set SecurityContextHolder to"
                        + SecurityContextHolder.getContext().getAuthentication()));
            } else {
                this.logger.debug("Set SecurityContextHolder to anonymous SecurityContext");
            }
        } else {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace(LogMessage.of(() -> "Did not set SecurityContextHolder since already authenticated"
                        + SecurityContextHolder.getContext().getAuthentication()));
            }
        }
        filterChain.doFilter(req, res);
    }

    /**
     * Constructs and returns an AnonymousAuthenticationToken representing an anonymous user.
     *
     * The token uses principal "anonymousUser", key "anonymousKey", and a single authority "ROLE_ANONYMOUS".
     *
     * @param request the current HTTP request (not used when creating the token)
     * @return an Authentication (AnonymousAuthenticationToken) for an anonymous user
     */
    private Authentication createAuthentication(HttpServletRequest request) {
        // 익명 사용자 principal 설정
        String anonymousPrincipal = "anonymousUser";

        // 익명 사용자 권한 설정
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS");

        // 익명 사용자 Authentication 객체 생성
        return new AnonymousAuthenticationToken("anonymousKey", anonymousPrincipal, authorities);
    }
}
