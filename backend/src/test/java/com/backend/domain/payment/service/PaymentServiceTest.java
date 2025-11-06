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
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("dev") // dev 프로파일 사용 (GeminiServiceTest 등 다른 테스트와 동일하게)
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

    // 테스트 시작 전 MockWebServer 실행
    @BeforeAll
    static void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    // 테스트 종료 후 MockWebServer 종료
    @AfterAll
    static void tearDownAll() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * @DynamicPropertySource
     * application.yml의 toss.api.url 프로퍼티를 동적으로 MockWebServer의 URL로 변경
     */
    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("toss.api.url", () -> mockWebServer.url("/").toString());
    }

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 깨끗한 상태를 위해 member, payment clear
        paymentRepository.deleteAll();
        memberRepository.deleteAll();

        // 테스트용 멤버 생성 및 저장
        testMember = Member.builder()
                .email("test@example.com")
                .name("테스트유저")
                .provider("TEST")
                .providerId("test-provider-id")
                .role(Role.FREE_USER) // 초기 등급은 FREE_USER
                .build();
        memberRepository.save(testMember);
    }

    // --- 1. requestPayment 테스트 ---

    @Test
    @DisplayName("결제 요청 성공: 유효한 금액으로 요청 시 READY 상태의 Payment 객체가 DB에 저장된다")
    void requestPayment_success() {
        // given
        Long validAmount = 29900L; // PLAN_30K 금액

        // when
        Payment payment = paymentService.requestPayment(testMember, validAmount);

        // then
        assertThat(payment).isNotNull();
        assertThat(payment.getAmount()).isEqualTo(validAmount);
        assertThat(payment.getMember()).isEqualTo(testMember);
        assertThat(payment.getStatus()).isEqualTo("READY");
        assertThat(payment.getOrderId()).isNotNull();

        // DB에 저장되었는지 검증
        Payment foundPayment = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
        assertThat(foundPayment.getOrderId()).isEqualTo(payment.getOrderId());
    }

    @Test
    @DisplayName("결제 요청 실패: 유효하지 않은 금액으로 요청 시 IllegalArgumentException이 발생한다")
    void requestPayment_fail_withInvalidAmount() {
        // given
        Long invalidAmount = 1000L; // 허용되지 않는 금액

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.requestPayment(testMember, invalidAmount);
        });

        assertThat(exception.getMessage()).isEqualTo("유효하지 않은 결제 금액입니다.");
    }

    // --- 2. confirmPayment 테스트 ---

    @Test
    @DisplayName("결제 승인 성공: 유효한 결제 승인 요청 시, 상태가 DONE으로 변경되고 멤버 등급이 상승한다")
    void confirmPayment_success() throws JsonProcessingException {
        // given
        // 1. "READY" 상태의 결제 정보 생성 (PLAN_50K)
        Long amount = 49900L;
        Payment readyPayment = paymentService.requestPayment(testMember, amount);
        String orderId = readyPayment.getOrderId();
        String paymentKey = "test-payment-key-123";

        // 2. MockWebServer가 반환할 가짜 Toss API 응답 (JSON) 생성
        TossConfirmResponseDto mockTossResponse = new TossConfirmResponseDto();
        // (TossConfirmResponseDto에 Setter가 없으므로, 실제 DTO 구조에 맞게 수정 필요)
        // 여기서는 objectMapper를 사용해 수동으로 JSON 문자열을 만듭니다.
        String mockResponseBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "status", "DONE",
                "totalAmount", amount
        ));

        // 3. MockWebServer에 가짜 응답 Enqueue
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mockResponseBody));

        // when
        // confirmPayment는 Mono를 반환하므로, StepVerifier로 비동기 검증
        StepVerifier.create(paymentService.confirmPayment(paymentKey, orderId, amount))
                .expectNextMatches(responseDto ->
                        responseDto.getStatus().equals("DONE") &&
                                responseDto.getOrderId().equals(orderId)
                )
                .verifyComplete();

        // then
        // 1. Payment 상태 검증 (DB)
        Payment confirmedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(confirmedPayment.getStatus()).isEqualTo("DONE");
        assertThat(confirmedPayment.getPaymentKey()).isEqualTo(paymentKey);

        // 2. Member 등급 검증 (DB)
        Member updatedMember = memberRepository.findById(testMember.getMemberId()).orElseThrow();
        assertThat(updatedMember.getRole()).isEqualTo(Role.PLAN_50K); // 49900L -> PLAN_50K
        assertThat(updatedMember.getPlanExpiresAt()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("결제 승인 실패: 요청 금액과 주문 금액이 일치하지 않으면 IllegalArgumentException이 발생한다")
    void confirmPayment_fail_whenAmountMismatch() {
        // given
        Long originalAmount = 29900L;
        Long mismatchedAmount = 100L; // 다른 금액
        Payment readyPayment = paymentService.requestPayment(testMember, originalAmount);
        String orderId = readyPayment.getOrderId();
        String paymentKey = "test-payment-key-456";

        // when & then
        // MockWebServer를 설정할 필요 없음. API 호출 전에 서비스 내부에서 검증됨.
        StepVerifier.create(paymentService.confirmPayment(paymentKey, orderId, mismatchedAmount))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                throwable.getMessage().equals("주문 금액이 일치하지 않습니다.")
                )
                .verify();

        // DB 상태가 변하지 않았는지 확인
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("READY");
        Member member = memberRepository.findById(testMember.getMemberId()).orElseThrow();
        assertThat(member.getRole()).isEqualTo(Role.FREE_USER);
    }

    @Test
    @DisplayName("결제 승인 실패: 존재하지 않는 주문 ID로 요청 시 NotFoundException이 발생한다")
    void confirmPayment_fail_whenOrderNotFound() {
        // given
        String nonExistentOrderId = "non-existent-order-id";
        String paymentKey = "test-payment-key-789";
        Long amount = 29900L;

        // when & then
        // 이 예외는 Mono.error()가 아닌, orElseThrow()에서 동기적으로 발생합니다.
        // 따라서 StepVerifier가 아닌 assertThrows로 검증해야 합니다.
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            paymentService.confirmPayment(paymentKey, nonExistentOrderId, amount).block(); // .block()으로 동기 실행
        });

        assertThat(exception.getMessage()).contains("주문 정보를 찾을 수 없습니다.");
    }
}