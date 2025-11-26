package com.backend.domain.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.backend.domain.admin.controller") // admin 컨트롤러들에만 적용
public class AdminPageControllerAdvice {

    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        // 모든 뷰에서 ${requestURI} 변수로 현재 주소를 쓸 수 있게 해줌
        return request.getRequestURI();
    }
}