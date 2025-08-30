package com.backend.controller;

import com.backend.dto.GenerateRequest;
import com.backend.dto.GenerateResponse;
import com.backend.service.GenerationService;
import com.backend.service.TaskService;
import com.backend.service.impl.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final GenerationService generationService;
    private final TaskService taskService;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(@RequestBody GenerateRequest request) {
        // 작업을 TaskService에 등록하고 즉시 taskId를 반환
        String taskId = taskService.submitTask(() ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return generationService.generateSpec(
                                request.getModel(),
                                request.getSpecExample(),
                                request.getProductNameExample()
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        Map<String, Object> result = taskService.getTaskResult(taskId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskService.cancelTask(taskId);
        return ResponseEntity.ok(Map.of("success", cancelled));
    }
}
