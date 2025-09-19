package com.backend.global.auth.service;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.global.auth.entity.MemberDetails;
import com.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    /**
     * Loads user details for Spring Security by member email.
     *
     * Looks up a Member by the provided email and returns a MemberDetails wrapper suitable for authentication.
     *
     * @param email the member's email used as the username; must correspond to an existing member
     * @return a MemberDetails instance representing the found member
     */
    @Override
    public MemberDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> NotFoundException.entityNotFound("Member"));
        return new MemberDetails(member);

    }
}
