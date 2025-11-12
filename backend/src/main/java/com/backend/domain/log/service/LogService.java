package com.backend.domain.log.service;

import com.backend.domain.log.dto.LogDetailDto;
import com.backend.domain.log.dto.LogSearchRequest;
import com.backend.domain.log.dto.LogSummaryDto;
import com.backend.domain.log.entity.GenerationLog;
import com.backend.domain.log.repository.GenerationLogRepository;
import com.backend.domain.member.entity.Member;
import com.backend.global.exception.NotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LogService {
    private final GenerationLogRepository logRepository;

    // 로그 목록을 동적 검색 조건으로 페이징 조회
    public Page<LogSummaryDto> searchLogs(LogSearchRequest searchRequest, Pageable pageable) {
        // Specification(동적 쿼리) 생성
        Specification<GenerationLog> spec = createSpecification(searchRequest);

        Page<GenerationLog> logPage = logRepository.findAll(spec, pageable);

        // 3. Page<GenerationLog> -> Page<LogSummaryDto>로 변환
        return logPage.map(LogSummaryDto::new);
    }

    // 로그 상세 조회 (Full JSON 포함)
    public LogDetailDto getLogDetail(Long generationLogId) {
        GenerationLog log = logRepository.findById(generationLogId)
                .orElseThrow(() -> NotFoundException.entityNotFound("Generation Log"));
        return new LogDetailDto(log);
    }

    // LogSearchRequest를 기반으로 JPA Specification 객체를 생성
    private Specification<GenerationLog> createSpecification(LogSearchRequest search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Member(Join) - Email 검색
            if (StringUtils.hasText(search.getMemberEmail())) {
                Join<GenerationLog, Member> memberJoin = root.join("member");
                predicates.add(cb.like(memberJoin.get("email"), "%" + search.getMemberEmail() + "%"));
            }

            // ModelName 검색
            if (StringUtils.hasText(search.getModelName())) {
                predicates.add(cb.like(root.get("modelName"), "%" + search.getModelName() + "%"));
            }

            // 성공/실패 여부 검색
            if (search.getSuccess() != null) {
                predicates.add(cb.equal(root.get("success"), search.getSuccess()));
            }

            // 날짜 범위 검색
            if (search.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), search.getStartDate().atStartOfDay()));
            }
            if (search.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), search.getEndDate().plusDays(1).atStartOfDay()));
            }

            // 정렬 순서 기본값: 최신순
            query.orderBy(cb.desc(root.get("createdAt")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}