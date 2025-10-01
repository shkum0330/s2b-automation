package com.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 현재 시스템의 CPU 코어 수를 기준으로 스레드 풀 크기를 동적으로 설정
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cores);      // 기본 스레드 수: CPU 코어 수
        executor.setMaxPoolSize(cores * 2);   // 최대 스레드 수: CPU 코어 수의 2배
        executor.setQueueCapacity(50);        // 대기 큐 크기
        executor.setThreadNamePrefix("GenSpec-");
        executor.initialize();
        return executor;
    }
}