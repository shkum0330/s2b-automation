package com.backend.domain.admin.controller;

import com.backend.domain.log.dto.LogDetailDto;
import com.backend.domain.log.dto.LogSearchRequest;
import com.backend.domain.log.dto.LogSummaryDto;
import com.backend.domain.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/log")
@RequiredArgsConstructor
public class AdminLogController {
    private final LogService logService;

    @GetMapping
    @Secured("ROLE_ADMIN") // 관리자만 접근 가능
    public ResponseEntity<Page<LogSummaryDto>> getLogs(
            @ModelAttribute LogSearchRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(logService.searchLogs(request, pageable));
    }

    @GetMapping("/{id}")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<LogDetailDto> getLogDetail(@PathVariable Long id) {
        return ResponseEntity.ok(logService.getLogDetail(id));
    }
}
