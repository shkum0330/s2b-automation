package com.backend.controller;

import com.backend.dto.GenerateRequest;
import com.backend.dto.GenerateResponse;
import com.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final GeminiService geminiService;

    @PostMapping("/generate-spec")
    public ResponseEntity<?> generateSpecification(@RequestBody GenerateRequest request) {
        try {
            log.info("요청 / {} {}", request.getExample(), request.getModel());
            GenerateResponse response = geminiService.generateSpec(request.getModel(), request.getExample());
            log.info("응답 / 상품명: {}, 규격: {}, 모델명: {}",response.getProductName(),response.getSpecification(),response.getModelName());
            log.info("응답 / kc: {}, 방송통신기자재: {}",response.getKcCertificationNumber(),response.getKatsCertificationNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing request: " + e.getMessage());
        }
    }
}
