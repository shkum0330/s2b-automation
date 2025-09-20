package com.backend.domain.generation.async;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 비동기 작업의 상태와 결과를 캡슐화하는 제네릭 DTO
 * @param <T> 결과 객체의 타입
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // JSON으로 변환 시 null 필드는 제외합니다.
public class TaskResult<T> {
    private final TaskStatus status;
    private final T result;
    private final String error;

    private TaskResult(TaskStatus status, T result, String error) {
        this.status = status;
        this.result = result;
        this.error = error;
    }

    public static <T> TaskResult<T> running() {
        return new TaskResult<>(TaskStatus.RUNNING, null, null);
    }

    public static <T> TaskResult<T> completed(T result) {
        return new TaskResult<>(TaskStatus.COMPLETED, result, null);
    }

    public static <T> TaskResult<T> failed(String errorMessage) {
        return new TaskResult<>(TaskStatus.FAILED, null, errorMessage);
    }

    public static <T> TaskResult<T> cancelled() {
        return new TaskResult<>(TaskStatus.CANCELLED, null, null);
    }

    public static <T> TaskResult<T> notFound() {
        return new TaskResult<>(TaskStatus.NOT_FOUND, null, null);
    }
}