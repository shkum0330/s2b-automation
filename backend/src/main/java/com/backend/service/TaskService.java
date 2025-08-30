package com.backend.service;

import com.backend.dto.GenerateResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class TaskService {
    // 실행 중인 작업들을 저장할 thread-safe 맵
    private final Map<String, CompletableFuture<GenerateResponse>> runningTasks = new ConcurrentHashMap<>();

    // 작업을 등록하고 즉시 Task ID를 반환하는 메서드
    public String submitTask(Supplier<CompletableFuture<GenerateResponse>> taskSupplier) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<GenerateResponse> future = taskSupplier.get()
                .whenComplete((result, error) -> {
                    // 작업 완료 또는 실패 시 맵에서 제거 (메모리 누수 방지)
                    if (error != null) {
                        // todo: 커스텀 예외 지정
                    }
                    runningTasks.remove(taskId);
                });
        runningTasks.put(taskId, future);
        return taskId;
    }

    // Task ID로 작업 상태와 결과를 조회하는 메서드
    public Map<String, Object> getTaskResult(String taskId) {
        CompletableFuture<GenerateResponse> future = runningTasks.get(taskId);

        if (future == null) {
            return Map.of("status", "NOT_FOUND");
        }
        if (future.isDone() && !future.isCancelled()) {
            try {
                return Map.of("status", "COMPLETED", "result", future.get());
            } catch (Exception e) {
                return Map.of("status", "FAILED", "error", e.getMessage());
            }
        }
        if (future.isCancelled()) {
            return Map.of("status", "CANCELLED");
        }

        return Map.of("status", "RUNNING");
    }

    // Task ID로 작업을 취소하는 메서드
    public boolean cancelTask(String taskId) {
        CompletableFuture<GenerateResponse> future = runningTasks.get(taskId);
        if (future != null && !future.isDone()) {
            // true는 인터럽트를 시도하여 작업을 강제 종료
            return future.cancel(true);
        }
        return false;
    }
}
