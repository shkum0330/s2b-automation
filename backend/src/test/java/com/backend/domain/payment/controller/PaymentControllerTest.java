package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.payment.dto.TossConfirmResponseDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.BillingKeyService;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private BillingKeyService billingKeyService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private Member createTestMember() {
        Member member = Member.builder()
                .email("test@example.com")
                .name("Tester")
                .role(Role.FREE_USER)
                .build();
        ReflectionTestUtils.setField(member, "memberId", 1L);
        return member;
    }

    private void setMockUser(Member member) {
        MemberDetails memberDetails = new MemberDetails(member);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(memberDetails, null, memberDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("결제 요청(POST /request) 테스트 - 성공")
    void requestPayment_success() throws Exception {
        Member member = createTestMember();
        setMockUser(member);

        Payment mockPayment = Payment.builder()
                .orderId("order_uuid_123")
                .amount(50000L)
                .orderName("테스트 상품")
                .member(member)
                .build();

        given(paymentService.requestPayment(any(), eq(50000L), eq("테스트 상품")))
                .willReturn(mockPayment);

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
        String paymentKey = "test_key";
        String orderId = "order_123";
        Long amount = 50000L;

        TossConfirmResponseDto mockResponse = new TossConfirmResponseDto();
        given(paymentService.confirmPayment(paymentKey, orderId, amount))
                .willReturn(Mono.just(mockResponse));

        mockMvc.perform(get("/api/v1/payments/success")
                        .param("paymentKey", paymentKey)
                        .param("orderId", orderId)
                        .param("amount", String.valueOf(amount)))
                .andExpect(request().asyncStarted())
                .andExpect(request().asyncResult("payment/success"))
                .andDo(print());
    }
}
