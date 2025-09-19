package com.backend.global.auth.refreshtoken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class RefreshToken {
    @Id
    private String keyEmail;

    private String refreshToken;

    private long expiration;

    /**
     * Creates a RefreshToken entity for the given email and token.
     *
     * <p>The token's expiration is initialized to 604800 seconds (7 days).</p>
     *
     * @param keyEmail the email used as the entity primary key
     * @param refreshToken the refresh token value
     */
    public RefreshToken(String keyEmail, String refreshToken) {
        this.keyEmail = keyEmail;
        this.refreshToken = refreshToken;
        this.expiration = 604800L; // 7일
    }

    /**
     * Replaces the stored refresh token and resets its expiration period.
     *
     * Sets the entity's refreshToken to the provided value and resets expiration to 604800 seconds (7 days).
     *
     * @param newRefreshToken the new refresh token value
     */
    public void updateToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
        this.expiration = 604800L; // 만료 시간 초기화
    }
}
