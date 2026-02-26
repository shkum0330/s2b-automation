package com.backend.domain.member.service;

import com.backend.domain.member.dto.MemberResponseDto;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("dev")
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .email("testuser@example.com")
                .name("테스트유저")
                .provider("TEST")
                .providerId("test-provider-id")
                .role(Role.PLAN_30K)
                .build();
    }

    @Test
    @DisplayName("회원 정보 조회 서비스가 정상 동작한다")
    void getMemberInfo_success() {
        // given
        memberRepository.save(testMember);

        // when
        MemberResponseDto responseDto = memberService.getMemberInfo(testMember);

        // then
        assertThat(responseDto).isNotNull();
        assertThat(responseDto.getEmail()).isEqualTo(testMember.getEmail());
        assertThat(responseDto.getName()).isEqualTo(testMember.getName());
        assertThat(responseDto.getRole()).isEqualTo(testMember.getRole().name());
        assertThat(responseDto.getCredit()).isEqualTo(testMember.getCredit());
    }

//    @Test
//    @DisplayName("크레딧 차감 성공 테스트")
//    void decrementCredit_success() {
//        // given
//        testMember.setCredit(10);
//        memberRepository.save(testMember);
//
//        // when
//        memberService.decrementCredit(testMember);
//
//        // then
//        Member foundMember = memberRepository.findByEmail("testuser@example.com").orElseThrow();
//        assertThat(foundMember.getCredit()).isEqualTo(9); // Member에 getCredit() 추가 후 주석 해제
//    }
//
//    @Test
//    @DisplayName("크레딧 부족 시 예외 발생 테스트")
//    void decrementCredit_fail_when_credit_is_insufficient() {
//        // given
//        memberRepository.save(testMember);
//
//        // when & then
//
//        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
//            memberService.decrementCredit(testMember);
//        });
//
//        assertThat(exception.getMessage()).isEqualTo("크레딧이 부족합니다.");
//    }
}


