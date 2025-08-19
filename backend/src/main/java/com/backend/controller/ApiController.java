package com.backend.controller;

import com.backend.dto.GenerateRequest;
import com.backend.dto.GenerateResponse;
import com.backend.service.GenerationService;
import com.backend.service.impl.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final GenerationService generationService;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(@RequestBody GenerateRequest request) {
        try {
            log.info("[요청] {} {}", request.getSpecExample(), request.getModel());
            GenerateResponse response = generationService.generateSpec(
                    request.getModel(),
                    request.getSpecExample(),
                    request.getProductNameExample()
            );
            log.info("[응답] / 상품명: {}, 규격: {}, 모델명: {}",response.getProductName(),response.getSpecification(),response.getModelName());
            log.info("[응답] / KC 인증번호: {}, 방송통신기자재 인증번호: {}",response.getKcCertificationNumber(),response.getKatsCertificationNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing request: " + e.getMessage());
        }
    }
}
