package com.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 메인 작업(AI, 스크래핑)용 스레드 풀
     */
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

    /**
     * 로깅 전용 스레드 풀
     * 메인 작업과 분리하여 후순위 작업을 처리
     */
    @Bean(name = "loggingTaskExecutor")
    public TaskExecutor loggingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // 로깅은 중요도가 낮으므로 적은 수의 스레드 할당
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100); // 작업이 몰릴 수 있으므로 큐는 넉넉하게 했음
        executor.setThreadNamePrefix("Log-");
        // 메인 스레드보다 우선순위를 낮춤 (1~10, 기본 5)
        executor.setThreadPriority(Thread.MIN_PRIORITY + 1);

        // 종료 시 큐에 대기 중인 모든 작업을 완료할 때까지 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 최대 대기 시간 설정 (예: 60초)
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}