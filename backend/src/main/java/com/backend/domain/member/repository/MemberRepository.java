package com.backend.domain.member.repository;

import com.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    Optional<Member> findByProviderId(String providerId);

    @Modifying
    @Query("update Member m set m.credit = m.credit - 1 where m.id = :id and m.credit > 0")
    int decrementCreditIfPossible(@Param("id") Long id);
}
