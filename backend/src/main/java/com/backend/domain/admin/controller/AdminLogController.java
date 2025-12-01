package com.backend.domain.admin.controller;

import com.backend.domain.log.dto.LogDetailDto;
import com.backend.domain.log.dto.LogSearchRequest;
import com.backend.domain.log.dto.LogSummaryDto;
import com.backend.domain.log.service.LogService;
import com.backend.domain.member.entity.Member;
import com.backend.global.auth.entity.MemberDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/log")
@RequiredArgsConstructor
public class AdminLogController {
    private final LogService logService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<LogSummaryDto>> getLogs(
            @ModelAttribute LogSearchRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("관리자 페이지 대시보드 조회");
        return ResponseEntity.ok(logService.searchLogs(request, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LogDetailDto> getLogDetail(@PathVariable Long id) {
        return ResponseEntity.ok(logService.getLogDetail(id));
    }
}
