package com.backend.global.config;

import com.backend.domain.generation.dto.GenerateResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    /**
     * Creates a Caffeine cache for tracking asynchronous generation tasks.
     *
     * <p>Keys are task IDs (String) and values are CompletableFuture&lt;GenerateResponse&gt; representing
     * in-progress or completed asynchronous results. Entries expire 10 minutes after being written
     * and the cache is capped at 1000 entries to limit concurrent tracked tasks.
     *
     * @return a configured Cache<String, CompletableFuture&lt;GenerateResponse&gt;>
     */
    @Bean
    public Cache<String, CompletableFuture<GenerateResponse>> taskCache() {
        // 작업 완료/실패 후 10분 동안 결과를 유지하고 자동으로 제거
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000) // 동시에 관리할 최대 작업 수
                .build();
    }
}