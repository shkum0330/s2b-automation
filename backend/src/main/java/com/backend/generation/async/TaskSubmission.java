package com.backend.generation.async;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@Getter
@RequiredArgsConstructor
public class TaskSubmission<T> {
    private final String taskId;
    private final CompletableFuture<T> future;
}