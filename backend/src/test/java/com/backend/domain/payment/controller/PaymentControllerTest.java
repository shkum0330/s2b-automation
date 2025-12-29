package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.payment.dto.PaymentRequestDto;
import com.backend.domain.payment.dto.TossConfirmResponseDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false) // 단위 테스트 집중을 위해 시큐리티 필터 비활성화
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    private Member createTestMember() {
        // 빌더로 기본 객체 생성 (ID 없음)
        Member member = Member.builder()
                .email("test@example.com")
                .name("Tester")
                .role(Role.FREE_USER)
                .build();

        // private 필드인 memberId에 강제로 값 주입
        ReflectionTestUtils.setField(member, "memberId", 1L);

        return member;
    }

    // @AuthenticationPrincipal 처리를 위한 Mock 사용자 설정
    private void setMockUser(Member member) {
        MemberDetails memberDetails = new MemberDetails(member);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(memberDetails, null, memberDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("결제 요청(POST /request) 테스트 - 성공")
    void requestPayment_success() throws Exception {
        // given
        Member member = createTestMember();
        setMockUser(member); // 로그인 상태 모방

        PaymentRequestDto requestDto = new PaymentRequestDto();
        // DTO에 Setter가 없다면 ReflectionTestUtils 등을 사용하거나 생성자 확인 필요
        // 여기서는 JSON으로 보낼 것이므로 내용은 mockMvc 호출 시 채워짐

        Payment mockPayment = Payment.builder()
                .orderId("order_uuid_123")
                .amount(50000L)
                .orderName("테스트 상품")
                .member(member)
                .build();

        given(paymentService.requestPayment(any(), eq(50000L), eq("테스트 상품")))
                .willReturn(mockPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50000, \"orderName\": \"테스트 상품\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order_uuid_123"))
                .andExpect(jsonPath("$.customerEmail").value("test@example.com"));
    }

    @Test
    @DisplayName("결제 성공 리다이렉트(GET /success) 테스트 - 성공 시 뷰 반환")
    void paymentSuccess_success() throws Exception {
        // given
        String paymentKey = "test_key";
        String orderId = "order_123";
        Long amount = 50000L;

        TossConfirmResponseDto mockResponse = new TossConfirmResponseDto();
        // DTO 필드 설정 (Setter 혹은 Reflection 필요)
        // 테스트 편의상 필요한 값만 Mocking 되었다고 가정

        given(paymentService.confirmPayment(paymentKey, orderId, amount))
                .willReturn(Mono.just(mockResponse)); // Mono 반환

        // when & then
        mockMvc.perform(get("/api/v1/payments/success")
                        .param("paymentKey", paymentKey)
                        .param("orderId", orderId)
                        .param("amount", String.valueOf(amount)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("payment/success")) // 뷰 이름 확인
                .andExpect(model().attributeExists("paymentKey", "orderId", "amount"));
    }
}