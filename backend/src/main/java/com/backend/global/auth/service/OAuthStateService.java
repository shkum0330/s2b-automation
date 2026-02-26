package com.backend.global.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private final Cache<String, Long> oauthStateCache;

    public String issueState() {
        String state = UUID.randomUUID().toString();
        oauthStateCache.put(state, System.currentTimeMillis());
        return state;
    }

    public boolean validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        Long issuedAt = oauthStateCache.asMap().remove(state);
        return issuedAt != null;
    }
}
