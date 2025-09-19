package com.backend.global.auth.refreshtoken;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Inserts or updates a refresh token for the given keyEmail.
     *
     * If a RefreshToken entity already exists for keyEmail, its token value is replaced and the entity is saved.
     * Otherwise a new RefreshToken is created and persisted.
     *
     * @param keyEmail     the identifier (email) used as the refresh token key
     * @param refreshToken the refresh token value to store
     */
    public void insertRefreshToken(String keyEmail, String refreshToken) {
        Optional<RefreshToken> findToken = refreshTokenRepository.findById(keyEmail);
        if (findToken.isPresent()){
            findToken.get().updateToken(refreshToken);
            refreshTokenRepository.save(findToken.get());
        } else {
            refreshTokenRepository.save(new RefreshToken(keyEmail, refreshToken));
        }
    }

    /**
     * Validates whether the stored refresh token for the given key (email) matches the provided token.
     *
     * @param keyEmail the identifier used as the refresh-token key (typically the user's email)
     * @param token the refresh token to validate against the stored value
     * @return true if a refresh token exists for {@code keyEmail} and equals {@code token}; false otherwise
     */
    public boolean validateRefreshToken(String keyEmail, String token) {
        return refreshTokenRepository.findById(keyEmail)
                .map(storedToken -> storedToken.getRefreshToken().equals(token))
                .orElse(false);
    }

    /**
     * Retrieves the refresh token entry for the given key email.
     *
     * @param keyEmail the key identifying the stored refresh token (typically a user's email)
     * @return an Optional containing the RefreshToken if found, or an empty Optional if not present
     */
    public Optional<RefreshToken> getRefreshToken(String keyEmail) {
        return refreshTokenRepository.findById(keyEmail);
    }

    /**
     * Removes the given refresh token entity from persistent storage.
     *
     * @param refreshToken the RefreshToken entity to delete from the repository
     */
    public void removeRefreshToken(RefreshToken refreshToken) {
        refreshTokenRepository.delete(refreshToken);
    }

    /**
     * Removes the stored refresh token for the given user identifier.
     *
     * Deletes the RefreshToken entity whose primary key is the provided email (keyEmail) if it exists.
     *
     * @param keyEmail the email used as the refresh token's identifier (primary key)
     */
    public void removeRefreshTokenByKeyEmail(String keyEmail) {
        refreshTokenRepository.deleteById(keyEmail);
    }
}
