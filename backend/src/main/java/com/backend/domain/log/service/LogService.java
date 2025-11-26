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
    private final GenerationLogRepository generationLogRepository;

    public Page<LogSummaryDto> searchLogs(LogSearchRequest request, Pageable pageable) {
        Specification<GenerationLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getMemberEmail())) {
                Join<GenerationLog, Member> memberJoin = root.join("member");
                predicates.add(cb.like(memberJoin.get("email"), "%" + request.getMemberEmail() + "%"));
            }
            if (request.getSuccess() != null) {
                predicates.add(cb.equal(root.get("success"), request.getSuccess()));
            }
            // 날짜 검색 등 추가 가능

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return generationLogRepository.findAll(spec, pageable).map(LogSummaryDto::new);
    }

    public LogDetailDto getLogDetail(Long id) {
        GenerationLog log = generationLogRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entityNotFound("Log not found"));
        return new LogDetailDto(log);
    }

    // LogSearchRequest를 기반으로 JPA Specification 객체를 생성
    private Specification<GenerationLog> createSpecification(LogSearchRequest search) {
        // Specification 인터페이스는 하나의 메서드(toPredicate)를 가진 함수형 인터페이스입니다.
        // root: 조회할 엔티티(GenerationLog 테이블)
        // query: 쿼리 자체 (ORDER BY 등을 설정)
        // cb (CriteriaBuilder): 조건문(WHERE, LIKE, EQUAL 등)을 만드는 공장
        return (root, query, cb) -> {

            // 1. 조건들을 담을 리스트 생성
            List<Predicate> predicates = new ArrayList<>();

            // 2. 동적 조건 추가: "사용자가 이메일 검색어를 입력했다면?"
            if (StringUtils.hasText(search.getMemberEmail())) {
                // SQL: JOIN member m ON log.member_id = m.id WHERE m.email LIKE '%검색어%'
                Join<GenerationLog, Member> memberJoin = root.join("member");
                predicates.add(cb.like(memberJoin.get("email"), "%" + search.getMemberEmail() + "%"));
            }

            // 3. 동적 조건 추가: "성공/실패 여부를 선택했다면?"
            if (search.getSuccess() != null) {
                // SQL: WHERE success = true (또는 false)
                predicates.add(cb.equal(root.get("success"), search.getSuccess()));
            }

            // 4. 정렬 조건 설정 (최신순)
            query.orderBy(cb.desc(root.get("createdAt")));

            // 5. 모든 조건을 AND로 묶어서 반환
            // SQL: WHERE 조건1 AND 조건2 AND ...
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}