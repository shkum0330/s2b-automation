package com.backend.domain.payment.service;

import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.domain.payment.dto.TossConfirmResponseDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.repository.PaymentRepository;
import com.backend.global.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("dev")
class PaymentServiceTest {

    public static MockWebServer mockWebServer;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Member testMember;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mockWebServer.shutdown();
    }

    // application.yml의 toss.api.url을 MockWebServer의 주소로 동적 교체
    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("toss.api.url", () -> mockWebServer.url("/").toString());
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        testMember = Member.builder()
                .email("test-" + suffix + "@example.com")
                .name("테스트유저")
                .provider("TEST")
                .providerId("tp-" + suffix)
                .role(Role.FREE_USER)
                .build();
        memberRepository.save(testMember);
    }

    @Test
    @DisplayName("유효한 결제 요청은 준비 상태로 저장된다")
    void requestPayment_success() {
        // given
        Long validAmount = 29900L;
        String orderName = "30일 10개 플랜";

        // when
        Payment payment = paymentService.requestPayment(testMember, validAmount, orderName);

        // then
        assertThat(payment).isNotNull();
        assertThat(payment.getAmount()).isEqualTo(validAmount);
        assertThat(payment.getOrderName()).isEqualTo(orderName);
        assertThat(payment.getMember()).isEqualTo(testMember);
        assertThat(payment.getStatus()).isEqualTo("READY");
        assertThat(payment.getOrderId()).isNotNull();

        // DB 검증
        Payment foundPayment = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
        assertThat(foundPayment.getOrderId()).isEqualTo(payment.getOrderId());
    }

    @Test
    @DisplayName("유효하지 않은 결제 금액은 예외가 발생한다")
    void requestPayment_fail_invalidAmount() {
        // given
        Long invalidAmount = 500L; // 정의되지 않은 금액
        String orderName = "이상한 플랜";

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.requestPayment(testMember, invalidAmount, orderName);
        });

        assertThat(exception.getMessage()).isEqualTo("유효하지 않은 결제 금액입니다.");
    }

    // --- 2. confirmPayment 테스트 ---

    @Test
    @DisplayName("결제 승인 성공 시 결제 상태와 멤버십이 갱신된다")
    void confirmPayment_success() throws JsonProcessingException {
        // given
        // 1. READY 상태의 결제 데이터 생성
        Long amount = 49900L;
        String orderName = "30일 20개 플랜";
        Payment readyPayment = paymentService.requestPayment(testMember, amount, orderName);
        String orderId = readyPayment.getOrderId();
        String paymentKey = "test_payment_key_success_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 2. MockWebServer 응답 설정 (Toss API 모킹)
        String mockResponseBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "status", "DONE",
                "totalAmount", amount
        ));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mockResponseBody));

        // when
        TossConfirmResponseDto responseDto = paymentService.confirmPayment(paymentKey, orderId, amount);

        // then
        assertThat(responseDto.getStatus()).isEqualTo("DONE");
        assertThat(responseDto.getOrderId()).isEqualTo(orderId);

        // 1. Payment 상태가 DONE으로 변경되었는지 확인
        Payment confirmedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(confirmedPayment.getStatus()).isEqualTo("DONE");
        assertThat(confirmedPayment.getPaymentKey()).isEqualTo(paymentKey);

        // 2. Member 등급이 PLAN_50K로 변경되었는지 확인
        Member updatedMember = memberRepository.findById(testMember.getMemberId()).orElseThrow();
        assertThat(updatedMember.getRole()).isEqualTo(Role.PLAN_50K);
        assertThat(updatedMember.getPlanExpiresAt()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("요청 금액이 다르면 결제는 중단 상태가 되고 예외가 발생한다")
    void confirmPayment_fail_amountMismatch() {
        // given
        Long originalAmount = 29900L;
        Long wrongAmount = 100L; // 요청 금액과 다름
        String orderName = "30일 10개 플랜";

        Payment readyPayment = paymentService.requestPayment(testMember, originalAmount, orderName);
        String orderId = readyPayment.getOrderId();
        String paymentKey = "test_payment_key_fail";

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.confirmPayment(paymentKey, orderId, wrongAmount);
        });
        assertThat(exception.getMessage()).isEqualTo("주문 금액이 일치하지 않습니다.");

        // then
        // 금액 불일치 시 failPayment()가 호출되어 status가 ABORTED로 변경되었는지 확인
        Payment failedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo("ABORTED");
    }

    @Test
    @DisplayName("존재하지 않는 주문 식별자로 승인 요청하면 예외가 발생한다")
    void confirmPayment_fail_notFound() {
        // given
        String nonExistentOrderId = "unknown_order_id";
        String paymentKey = "test_key";
        Long amount = 29900L;

        // when & then
        // orElseThrow()는 Mono 스트림 생성 전에 발생하므로 assertThrows 사용
        assertThrows(NotFoundException.class, () -> {
            paymentService.confirmPayment(paymentKey, nonExistentOrderId, amount);
        });
    }
}





