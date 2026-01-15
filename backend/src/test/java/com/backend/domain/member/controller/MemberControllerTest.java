package com.backend.domain.member.controller;

import com.backend.domain.member.dto.MemberResponseDto;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.service.MemberService;
import com.backend.global.auth.entity.MemberDetails;
import com.backend.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// [중요] JPA 매핑 컨텍스트 import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 정보 조회 API(GET /api/v1/members/me)가 인증된 사용자 요청을 처리하고 올바른 포맷의 응답을 반환하는지 검증
 */
@WebMvcTest(MemberController.class)
@AutoConfigureRestDocs
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfoTest() throws Exception {
        // given
        Member member = Member.builder()
                .name("테스트유저")
                .email("test@example.com")
                .role(Role.FREE_USER)
                .build();

        ReflectionTestUtils.setField(member, "memberId", 1L);
        ReflectionTestUtils.setField(member, "credit", 100);
        ReflectionTestUtils.setField(member, "dailyRequestCount", 5);

        MemberDetails memberDetails = new MemberDetails(member);
        MemberResponseDto responseDto = new MemberResponseDto(member);

        given(memberService.getMemberInfo(any(Member.class))).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/members/me")
                        .with(user(memberDetails))
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.credit").value(100))
                .andDo(document("member-get-me",
                        responseFields(
                                fieldWithPath("email").description("사용자 이메일"),
                                fieldWithPath("name").description("사용자 이름"),
                                fieldWithPath("role").description("사용자 등급 (FREE_USER 등)"),
                                fieldWithPath("credit").description("보유 크레딧"),
                                fieldWithPath("dailyRequestCount").description("일일 사용 가능 횟수")
                        )
                ));
    }
}