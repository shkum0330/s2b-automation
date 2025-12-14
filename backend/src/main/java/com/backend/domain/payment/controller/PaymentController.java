package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.payment.dto.PaymentRequestDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${toss.widget-secret-key}")
    private String widgetSecretKey;

    @Value("${toss.secret-key}")
    private String apiSecretKey;

    private final Map<String, String> billingKeyMap = new HashMap<>();

    // 결제 요청(주문 생성)
    @PostMapping("/request")
    @ResponseBody
    public ResponseEntity<?> requestPayment(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody PaymentRequestDto requestDto
    ) {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            Member member = memberDetails.member();
            // 보안상 랜덤 문자열을 섞는 것이 좋으나 테스트용으로 ID 사용
            String customerKey = "CUSTOMER_" + member.getMemberId();

            Payment payment = paymentService.requestPayment(member, requestDto.getAmount(), requestDto.getOrderName());

            return ResponseEntity.ok(Map.of(
                    "orderId", payment.getOrderId(),
                    "amount", payment.getAmount(),
                    "orderName", payment.getOrderName(),
                    "customerEmail", member.getEmail(),
                    "customerName", member.getName() != null ? member.getName() : "회원",
                    "customerKey", customerKey
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 결제 성공 리다이렉트 처리
    @GetMapping("/success")
    public Mono<String> paymentSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount,
            Model model) {

        log.info("결제 승인 요청 (Redirect) - orderId: {}, amount: {}", orderId, amount);

        // confirmPayment는 WebClient를 쓰므로 Mono를 반환
        return paymentService.confirmPayment(paymentKey, orderId, amount)
                .map(response -> {
                    // 승인 성공 시 보여줄 뷰 데이터 설정
                    model.addAttribute("orderId", response.getOrderId());
                    model.addAttribute("amount", response.getTotalAmount());
                    model.addAttribute("paymentKey", response.getPaymentKey());

                    return "payment/success";
                })
                .onErrorResume(e -> {
                    log.error("결제 승인 실패", e);
                    model.addAttribute("message", e.getMessage());
                    model.addAttribute("code", "CONFIRM_FAILED");
                    return Mono.just("payment/fail");
                });
    }

    // 결제 승인
    @PostMapping(value = {"/confirm/widget", "/confirm/payment"})
    @ResponseBody
    public ResponseEntity<JsonNode> confirmPayment(HttpServletRequest request, @RequestBody String jsonBody) throws Exception {
        String secretKey = request.getRequestURI().contains("/confirm/payment") ? apiSecretKey : widgetSecretKey;

        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, secretKey, "https://api.tosspayments.com/v1/payments/confirm");

        int statusCode = response.has("error") ? 400 : 200;
        return ResponseEntity.status(statusCode).body(response);
    }

    // 자동결제 (빌링) 승인
    @PostMapping("/confirm-billing")
    @ResponseBody
    public ResponseEntity<JsonNode> confirmBilling(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String billingKey = billingKeyMap.get(requestData.get("customerKey").asText());

        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/" + billingKey);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // 빌링키 발급
    @PostMapping("/issue-billing-key")
    @ResponseBody
    public ResponseEntity<JsonNode> issueBillingKey(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/authorizations/issue");

        if (!response.has("error")) {
            billingKeyMap.put(requestData.get("customerKey").asText(), response.get("billingKey").asText());
        }

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // 브랜드페이 인증 콜백
    @GetMapping("/callback-auth")
    @ResponseBody
    public ResponseEntity<JsonNode> callbackAuth(@RequestParam String customerKey, @RequestParam String code) throws Exception {
        ObjectNode requestData = objectMapper.createObjectNode();
        requestData.put("grantType", "AuthorizationCode");
        requestData.put("customerKey", customerKey);
        requestData.put("code", code);

        String url = "https://api.tosspayments.com/v1/brandpay/authorizations/access-token";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);

        log.info("Response Data: {}", response);

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // 브랜드페이 승인
    @PostMapping("/confirm/brandpay")
    @ResponseBody
    public ResponseEntity<JsonNode> confirmBrandpay(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String url = "https://api.tosspayments.com/v1/brandpay/payments/confirm";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // 결제 페이지 반환
    @GetMapping("/")
    public String index() {
        // static 폴더의 HTML 파일을 보여주기 위해 forward 사용
        return "forward:/widget/checkout.html";
    }

    // 실패 페이지 반환
    @GetMapping("/fail")
    public String failPayment(HttpServletRequest request, Model model) {
        model.addAttribute("code", request.getParameter("code"));
        model.addAttribute("message", request.getParameter("message"));
        return "forward:/fail.html";
    }

    // Toss API 호출 (HttpURLConnection + Jackson)
    private JsonNode sendRequest(JsonNode requestData, String secretKey, String urlString) throws IOException {
        HttpURLConnection connection = createConnection(secretKey, urlString);

        try (OutputStream os = connection.getOutputStream()) {
            // JsonNode를 byte array로 변환하여 전송
            os.write(objectMapper.writeValueAsBytes(requestData));
        }

        try (InputStream responseStream = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream();
             Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8)) {
            // 응답 스트림을 JsonNode로 파싱
            return objectMapper.readTree(reader);
        } catch (Exception e) {
            log.error("Error reading response", e);
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Error reading response");
            return errorResponse;
        }
    }

    private HttpURLConnection createConnection(String secretKey, String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        return connection;
    }
}