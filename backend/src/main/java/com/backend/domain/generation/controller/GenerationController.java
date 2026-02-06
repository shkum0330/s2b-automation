package com.backend.domain.generation.controller;

import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.async.TaskResult;
import com.backend.domain.generation.dto.GenerateNonElectronicRequest;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;
import com.backend.domain.generation.service.GenerationService;
import com.backend.domain.generation.service.TaskService;
import com.backend.global.auth.entity.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/generation")
public class GenerationController {

    private final GenerationService generationService;
    private final TaskService taskService;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(
            @Valid @RequestBody GenerateElectronicRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        log.info("서비스 호출 전: {}", request);
        // 여러 API를 호출하고 조합하는 비동기 작업
        CompletableFuture<GenerateElectronicResponse> future = generationService.generateSpec(
                request,
                memberDetails.member()
        );
        log.info("서비스 호출 후: {}", request);
        // TaskService에 작업을 등록하고 클라이언트가 폴링할 수 있도록 taskId를 반환
        String taskId = taskService.submitTask(future);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    @PostMapping("/generate-general-spec")
    public ResponseEntity<Map<String, String>> generateGeneralSpecification(
            @Valid @RequestBody GenerateNonElectronicRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        log.info("비전자제품 생성 요청: memberId={}, product={}", memberDetails.member().getMemberId(), request.getProductName());

        // 비동기 작업 시작
        CompletableFuture<GenerateNonElectronicResponse> future = generationService.generateGeneralSpec(
                request,
                memberDetails.member()
        );

        // future.join() 대기 코드를 삭제하고, taskId를 즉시 반환하도록 수정
        String taskId = taskService.submitTask(future);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
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
}