package com.backend.domain.admin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminViewController {

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @GetMapping("/login")
    public String login(Model model) {
        // 뷰(HTML)에서 사용할 수 있도록 모델에 담음
        model.addAttribute("kakaoClientId", kakaoClientId);
        return "admin/login";
    }


    @GetMapping("/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }
}
