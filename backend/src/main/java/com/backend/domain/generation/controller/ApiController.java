package com.backend.domain.generation.controller;

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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/generation")
public class ApiController {
    private final MemberService memberService;
    private final GenerationService generationService;
    private final TaskService taskService;

    // 동기 응답을 시도할 최대 대기 시간 (초)
    private static final long RESPONSE_TIMEOUT_SECONDS = 60;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(@RequestBody GenerateRequest request,
                                                   @AuthenticationPrincipal MemberDetails memberDetails) {

        // 1. GenerationService가 직접 CompletableFuture를 반환
        CompletableFuture<GenerateResponse> future = generationService.generateSpec(
                request.getModel(),
                request.getSpecExample(),
                request.getProductNameExample(),
                memberDetails.member() // 사용자 정보 전달
        );

        // 2. 생성된 Future를 TaskService에 등록하고 taskId를 받음
        String taskId = taskService.submitTask(future);

        try {
            // 3. 해당 Future를 직접 사용하여 결과를 기다림
            GenerateResponse response = future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
//            log.info("응답 성공: Task {}", taskId);
            return ResponseEntity.ok(response);

        } catch (TimeoutException e) {
//            log.warn("Task {}가 시간 초과되어 폴링 방식으로 전환합니다.", taskId);
            return ResponseEntity.accepted().body(Map.of("taskId", taskId));

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
//            log.error("Task {} 실행 중 예외 발생", taskId, cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(TaskResult.failed(cause.getMessage()));

        } catch (CancellationException e) {
//            log.info("Task {}가 대기 중 취소되었습니다.", taskId);
            return ResponseEntity.ok(TaskResult.cancelled());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
//            log.error("Task {} 처리 중 스레드가 중단되었습니다.", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Request processing was interrupted."));
        }
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