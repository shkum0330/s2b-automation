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

        // 여러 API를 호출하고 조합하는 비동기 작업
        CompletableFuture<GenerateElectronicResponse> future = generationService.generateSpec(
                request,
                memberDetails.member()
        );

        // TaskService에 작업을 등록하고 클라이언트가 폴링할 수 있도록 taskId를 반환
        String taskId = taskService.submitTask(future);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    @PostMapping("/generate-general-spec")
    public ResponseEntity<?> generateGeneralSpecification(
            @Valid @RequestBody GenerateNonElectronicRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        // 단일 AI API만 호출하는 비동기 작업
        CompletableFuture<GenerateNonElectronicResponse> future = generationService.generateGeneralSpec(
                request,
                memberDetails.member()
        );

        try {
            GenerateNonElectronicResponse result = future.join();

            // Map.of 대신 HashMap 사용 (null 허용을 위해)
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("taskId", null);
            log.info("생성 결과: {}",result.toString());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw new CompletionException(e.getCause());
        }
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