package com.backend.domain.payment.controller;

import com.backend.domain.member.entity.Member;
import com.backend.domain.payment.dto.PaymentConfirmRequestDto;
import com.backend.domain.payment.dto.PaymentRequestDto;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.service.BillingKeyService;
import com.backend.domain.payment.service.PaymentService;
import com.backend.global.auth.entity.MemberDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final int HTTP_CONNECT_TIMEOUT_MS = 5000;
    private static final int HTTP_READ_TIMEOUT_MS = 30000;

    private final PaymentService paymentService;
    private final BillingKeyService billingKeyService;
    private final ObjectMapper objectMapper;

    @Value("${toss.secret-key}")
    private String apiSecretKey;

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
            Payment payment = paymentService.requestPayment(member, requestDto.getAmount(), requestDto.getOrderName());
            String customerKey = "CUSTOMER_" + payment.getOrderId();

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

    @GetMapping("/success")
    public Mono<String> paymentSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount,
            Model model
    ) {
        log.info("결제 확인 요청 (Redirect) - orderId: {}, amount: {}", orderId, amount);

        return paymentService.confirmPayment(paymentKey, orderId, amount)
                .map(response -> {
                    model.addAttribute("orderId", response.getOrderId());
                    model.addAttribute("amount", response.getTotalAmount());
                    model.addAttribute("paymentKey", response.getPaymentKey());
                    return "payment/success";
                })
                .onErrorResume(e -> {
                    log.error("결제 확인 실패", e);
                    model.addAttribute("message", e.getMessage());
                    model.addAttribute("code", "CONFIRM_FAILED");
                    return Mono.just("payment/fail");
                });
    }

    @PostMapping(value = {"/confirm/widget", "/confirm/payment"})
    @ResponseBody
    public Mono<ResponseEntity<Object>> confirmPayment(@Valid @RequestBody PaymentConfirmRequestDto requestDto) {
        return paymentService.confirmPayment(
                        requestDto.getPaymentKey(),
                        requestDto.getOrderId(),
                        requestDto.getAmount()
                )
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(e -> {
                    String message = (e.getMessage() == null || e.getMessage().isBlank())
                            ? "결제 승인 처리 중 오류가 발생했습니다."
                            : e.getMessage();
                    return Mono.just(ResponseEntity.badRequest().body(Map.of("message", message)));
                });
    }

    @PostMapping("/confirm-billing")
    @ResponseBody
    public ResponseEntity<JsonNode> confirmBilling(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody String jsonBody
    ) throws Exception {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode customerKeyNode = requestData.get("customerKey");
        if (customerKeyNode == null || customerKeyNode.asText().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("INVALID_CUSTOMER_KEY", "customerKey가 유효하지 않습니다."));
        }

        String billingKey = billingKeyService.findBillingKey(customerKeyNode.asText()).orElse(null);
        if (billingKey == null || billingKey.isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("INVALID_BILLING_KEY", "유효한 billingKey가 없습니다."));
        }

        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/" + billingKey);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @PostMapping("/issue-billing-key")
    @ResponseBody
    public ResponseEntity<JsonNode> issueBillingKey(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody String jsonBody
    ) throws Exception {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/authorizations/issue");

        if (!response.has("error")) {
            JsonNode customerKeyNode = requestData.get("customerKey");
            JsonNode billingKeyNode = response.get("billingKey");
            if (customerKeyNode != null && !customerKeyNode.asText().isBlank() && billingKeyNode != null) {
                billingKeyService.saveBillingKey(customerKeyNode.asText(), billingKeyNode.asText());
            }
        }

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @GetMapping("/callback-auth")
    @ResponseBody
    public ResponseEntity<JsonNode> callbackAuth(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam String customerKey,
            @RequestParam String code
    ) throws Exception {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        ObjectNode requestData = objectMapper.createObjectNode();
        requestData.put("grantType", "AuthorizationCode");
        requestData.put("customerKey", customerKey);
        requestData.put("code", code);

        String url = "https://api.tosspayments.com/v1/brandpay/authorizations/access-token";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);
        log.info("Response Data: {}", response);

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @PostMapping("/confirm/brandpay")
    @ResponseBody
    public ResponseEntity<JsonNode> confirmBrandpay(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody String jsonBody
    ) throws Exception {
        if (memberDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        JsonNode requestData = objectMapper.readTree(jsonBody);
        String url = "https://api.tosspayments.com/v1/brandpay/payments/confirm";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @GetMapping("/")
    public String index() {
        return "forward:/widget/checkout.html";
    }

    @GetMapping("/fail")
    public String failPayment(HttpServletRequest request, Model model) {
        model.addAttribute("code", request.getParameter("code"));
        model.addAttribute("message", request.getParameter("message"));
        return "forward:/fail.html";
    }

    private JsonNode sendRequest(JsonNode requestData, String secretKey, String urlString) throws IOException {
        HttpURLConnection connection = createConnection(secretKey, urlString);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(objectMapper.writeValueAsBytes(requestData));
        }

        try (InputStream responseStream = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream();
             Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8)) {
            return objectMapper.readTree(reader);
        } catch (Exception e) {
            log.error("Error reading response", e);
            return errorResponse("READ_RESPONSE_FAILED", "Error reading response");
        }
    }

    private HttpURLConnection createConnection(String secretKey, String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Authorization",
                "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(HTTP_READ_TIMEOUT_MS);
        return connection;
    }

    private ObjectNode errorResponse(String code, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
