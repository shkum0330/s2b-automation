package com.backend.global.auth.refreshtoken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class RefreshToken {
    @Id
    private String keyEmail;

    private String refreshToken;

    private Instant expiresAt;

    public RefreshToken(String keyEmail, String refreshToken) {
        this.keyEmail = keyEmail;
        this.refreshToken = refreshToken;
        this.expiresAt = Instant.now().plus(Duration.ofDays(7));
    }

    public void updateToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
        this.expiresAt = Instant.now().plus(Duration.ofDays(7));
    }
}
