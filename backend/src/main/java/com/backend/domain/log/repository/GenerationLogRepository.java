package com.backend.domain.log.repository;

import com.backend.domain.log.GenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationLogRepository extends JpaRepository<GenerationLog, Long> {
}
