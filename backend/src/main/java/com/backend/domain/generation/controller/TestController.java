package com.backend.domain.generation.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class TestController {

    /**
     * GET /test endpoint that logs "api 정상 호출" for basic health-check/connection verification.
     *
     * <p>No request parameters and no response body (returns 200 OK by default).</p>
     */
    @GetMapping("/test")
    public void test(){
        log.info("api 정상 호출");
    }
}
