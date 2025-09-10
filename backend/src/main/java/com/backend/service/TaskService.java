package com.backend.service;

import com.backend.dto.GenerateResponse;
import com.backend.dto.async.TaskResult;
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
    // 메모리 누수와 결과 유실 문제를 해결하기 위해 캐시 사용
    private final Cache<String, CompletableFuture<GenerateResponse>> taskCache;
    private final Executor taskExecutor; // 생성자에 Executor 추가

    // Supplier 대신 CompletableFuture를 직접 받도록 수정
    public String submitTask(CompletableFuture<GenerateResponse> future) {
        String taskId = UUID.randomUUID().toString();

        // 전달받은 future에 콜백을 연결
        future.whenCompleteAsync((result, error) -> {
            if (error != null) {
                log.error("Task {} failed.", taskId, error.getCause());
            } else {
                log.info("Task {} completed successfully.", taskId);
            }
        }, taskExecutor);

        taskCache.put(taskId, future);
        return taskId;
    }


    public TaskResult<GenerateResponse> getTaskResult(String taskId) {
        CompletableFuture<GenerateResponse> future = taskCache.getIfPresent(taskId);

        if (future == null) {
            // 캐시에 작업이 없으면, 만료되었거나 존재하지 않는 ID
            return TaskResult.notFound();
        }

        if (future.isDone()) {
            if (future.isCancelled()) {
                return TaskResult.cancelled();
            }
            // isDone()이 true이고 취소되지 않았다면, 성공 또는 실패 상태
            try {
                // .get()은 성공 시 결과를 반환하고, 실패 시 예외를 던짐
                GenerateResponse result = future.get();
                return TaskResult.completed(result);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                String errorMessage = (cause != null) ? cause.getMessage() : e.getMessage();
                return TaskResult.failed(errorMessage);
            }
        }
        // 작업이 아직 완료되지 않았다면 RUNNING 상태
        return TaskResult.running();
    }

    public boolean cancelTask(String taskId) {
        CompletableFuture<GenerateResponse> future = taskCache.getIfPresent(taskId);
        if (future != null && !future.isDone()) {
            // true 파라미터는 실행 중인 스레드를 인터럽트하려고 시도
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                log.info("Task {} 성공적으로 취소됨", taskId);
            }
            return cancelled;
        }
        return false;
    }
}