package com.backend.domain.generation.controller;

import com.backend.domain.generation.dto.GenerateGeneralRequest;
import com.backend.domain.generation.dto.GenerateGeneralResponse;
import com.backend.domain.generation.dto.GenerateRequest;
import com.backend.domain.generation.dto.GenerateResponse;
import com.backend.domain.generation.async.TaskResult;
import com.backend.domain.generation.service.GenerationService;
import com.backend.domain.generation.service.TaskService;
import com.backend.domain.member.service.MemberService;
import com.backend.global.auth.entity.MemberDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/generation")
public class GenerationController {

    private final GenerationService generationService;
    private final TaskService taskService;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(
            @RequestBody GenerateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        CompletableFuture<GenerateResponse> future = generationService.generateSpec(
                request.getModel(),
                request.getSpecExample(),
                request.getProductNameExample(),
                memberDetails.member()
        );

        return processGenerationTask(future, "전자제품");
    }

    @PostMapping("/generate-general-spec")
    public ResponseEntity<?> generateGeneralSpecification(
            @RequestBody GenerateGeneralRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        CompletableFuture<GenerateGeneralResponse> future = generationService.generateGeneralSpec(
                request.getProductName(),
                request.getSpecExample(),
                memberDetails.member()
        );

        return processGenerationTask(future, "비전자제품");
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<TaskResult<?>> getResult(@PathVariable String taskId) {
        TaskResult<?> result = taskService.getTaskResult(taskId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskService.cancelTask(taskId);
        return ResponseEntity.ok(Map.of("success", cancelled));
    }

    /**
     * 비동기 생성 작업을 TaskService에 등록하고 taskId를 반환하는 공통 로직
     * @param future 처리할 비동기 작업
     * @param taskType 로깅을 위한 작업 유형 문자열
     * @return taskId가 담긴 ResponseEntity
     */
    private ResponseEntity<?> processGenerationTask(CompletableFuture<?> future, String taskType) {
        String taskId = taskService.submitTask(future);
        log.info("{} 작업(Task ID: {})이 접수되어 폴링 방식으로 전환합니다.", taskType, taskId);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }
}