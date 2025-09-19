package com.backend.domain.member.repository;

import com.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    /**
 * Finds a Member by email.
 *
 * @param email the member's email address to look up
 * @return an Optional containing the Member if found, otherwise Optional.empty()
 */
Optional<Member> findByEmail(String email);

    /**
 * Finds a Member by their external authentication provider identifier.
 *
 * @param providerId the identifier assigned to the user by an external authentication provider (e.g., OAuth provider user ID)
 * @return an Optional containing the Member if found, otherwise Optional.empty()
 */
Optional<Member> findByProviderId(String providerId);


}
