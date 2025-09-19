package com.backend.global.auth.entity;

import com.backend.domain.member.entity.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

public record MemberDetails (Member member) implements UserDetails {

    /**
     * Returns the authorities granted to the user.
     *
     * <p>Produces a single {@link SimpleGrantedAuthority} whose authority string is
     * "ROLE_" concatenated with the wrapped member's role name, and returns it as an immutable singleton collection.
     *
     * @return an immutable singleton collection containing the member's role as a {@link SimpleGrantedAuthority}
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = "ROLE_" + member.getRole().name();

        // SimpleGrantedAuthority 객체로 감싸고, 불변 컬렉션인 singletonList로 반환
        return Collections.singletonList(new SimpleGrantedAuthority(authority));
    }

    /**
     * This UserDetails implementation does not store or expose a password.
     *
     * @return null always; password information is not available from this object
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * Returns the username for authentication, using the wrapped Member's email.
     *
     * @return the member's email used as the Spring Security username
     */
    @Override
    public String getUsername() {
        return member().getEmail();
    }

    /**
     * Indicates whether the user's account is non-expired.
     *
     * Always returns {@code true}; this implementation does not track account expiration and treats all accounts as non-expired.
     *
     * @return {@code true} if the account is non-expired
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user's credentials are non-expired.
     *
     * @return true always, meaning credentials are considered non-expired for this UserDetails implementation
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user's account is enabled.
     *
     * @return true always — this implementation treats the account as enabled
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
