package com.backend.domain.generation.service;

import com.backend.domain.generation.async.TaskResult;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
    private final Cache<String, CompletableFuture<?>> taskCache;
    private final Executor taskExecutor;

    public <T> String submitTask(CompletableFuture<T> future) {
        String taskId = UUID.randomUUID().toString();

        future.whenCompleteAsync((result, error) -> {
            if (error != null) {
                // todo: 에러 로깅 방식 변경
                log.error("Task {} failed.", taskId, error.getCause() != null ? error.getCause() : error);
            } else {
                log.info("Task {} completed successfully.", taskId);
            }
        }, taskExecutor);

        taskCache.put(taskId, future);
        return taskId;
    }

    @SuppressWarnings("unchecked") // 타입 캐스팅 경고를 무시
    public <T> TaskResult<T> getTaskResult(String taskId) {
        CompletableFuture<T> future = (CompletableFuture<T>) taskCache.getIfPresent(taskId);

        if (future == null) {
            return TaskResult.notFound();
        }

        if (future.isDone()) {
            if (future.isCancelled()) {
                return TaskResult.cancelled();
            }
            try {
                T result = future.get();
                return TaskResult.completed(result);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                String errorMessage = (cause != null) ? cause.getMessage() : e.getMessage();
                return TaskResult.failed(errorMessage);
            }
        }
        return TaskResult.running();
    }

    // --- [MODIFIED] ---
    // cancelTask도 모든 타입의 Future를 취소할 수 있도록 수정
    public boolean cancelTask(String taskId) {
        CompletableFuture<?> future = taskCache.getIfPresent(taskId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                log.info("Task {} 성공적으로 취소됨", taskId);
            }
            return cancelled;
        }
        return false;
    }
}