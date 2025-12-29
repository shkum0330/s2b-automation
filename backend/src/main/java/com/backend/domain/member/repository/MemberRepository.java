package com.backend.domain.member.repository;

import com.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    Optional<Member> findByProviderId(String providerId);

    /**
     * 일일 사용 한도(dailyLimit)를 초과하지 않은 경우에만 dailyRequestCount를 1 증가시킵니다.
     * @return 쿼리로 인해 변경된 row 수 (성공 시 1, 실패 시 0)
     */
    @Modifying
    @Query("update Member m set m.dailyRequestCount = m.dailyRequestCount + 1 " +
            "where m.memberId = :id and m.dailyRequestCount < :dailyLimit")
    int incrementDailyCountIfPossible(@Param("id") Long id, @Param("dailyLimit") int dailyLimit);
}