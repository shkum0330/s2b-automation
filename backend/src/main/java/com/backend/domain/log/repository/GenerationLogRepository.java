package com.backend.domain.log.repository;

import com.backend.domain.log.entity.GenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GenerationLogRepository extends JpaRepository<GenerationLog, Long>,
        JpaSpecificationExecutor<GenerationLog> {
}