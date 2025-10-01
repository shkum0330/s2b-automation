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
    private final MemberService memberService;
    private final GenerationService generationService;
    private final TaskService taskService;

    // 동기 응답을 시도할 최대 대기 시간 (초)
    private static final long RESPONSE_TIMEOUT_SECONDS = 60;

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

        // TaskService에 작업을 등록하고 taskId를 받음
        String taskId = taskService.submitTask(future);

        // 동기 대기 로직(try-catch)을 제거하고, taskId를 즉시 반환
        log.info("전자제품 작업(Task ID: {})이 접수되어 폴링 방식으로 전환합니다.", taskId);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    // --- [NEW] 비전자제품용 API 엔드포인트 추가 ---
    @PostMapping("/generate-general-spec")
    public ResponseEntity<?> generateGeneralSpecification(
            @RequestBody GenerateGeneralRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        CompletableFuture<GenerateGeneralResponse> future = generationService.generateGeneralSpec(
                request.getProductName(),
                request.getSpecExample(),
                memberDetails.member()
        );

        String taskId = taskService.submitTask(future);

        log.info("비전자제품 작업(Task ID: {})이 접수되어 폴링 방식으로 전환합니다.", taskId);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }


    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        TaskResult<GenerateResponse> result = taskService.getTaskResult(taskId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskService.cancelTask(taskId);
        return ResponseEntity.ok(Map.of("success", cancelled));
    }
}