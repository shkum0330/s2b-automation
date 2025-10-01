package com.backend.global.config;

import com.backend.domain.generation.dto.GenerateResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, CompletableFuture<?>> taskCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .removalListener((String key, CompletableFuture<?> future, RemovalCause cause) -> {
                    if (future != null && !future.isDone()) {
                        future.cancel(true);
                    }
                })
                .build();
    }
}