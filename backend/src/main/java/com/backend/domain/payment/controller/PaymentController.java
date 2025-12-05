package com.backend.domain.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${toss.widget-secret-key}")
    private String widgetSecretKey;
    @Value("${toss.secret-key}")
    private String apiSecretKey;

    private final Map<String, String> billingKeyMap = new HashMap<>();

    // --- [1. API 메서드] : 반드시 @ResponseBody가 필요합니다 ---

    @PostMapping(value = {"/confirm/widget", "/confirm/payment"})
    @ResponseBody // [핵심 수정] 이게 없으면 HTML을 찾으려고 해서 에러가 납니다.
    public ResponseEntity<JsonNode> confirmPayment(HttpServletRequest request, @RequestBody String jsonBody) throws Exception {
        String secretKey = request.getRequestURI().contains("/confirm/payment") ? apiSecretKey : widgetSecretKey;
        log.info("시크릿키: {}", secretKey);

        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, secretKey, "https://api.tosspayments.com/v1/payments/confirm");

        int statusCode = response.has("error") ? 400 : 200;
        return ResponseEntity.status(statusCode).body(response);
    }

    @PostMapping("/confirm-billing")
    @ResponseBody // [추가]
    public ResponseEntity<JsonNode> confirmBilling(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String billingKey = billingKeyMap.get(requestData.get("customerKey").asText());
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/" + billingKey);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @PostMapping("/issue-billing-key")
    @ResponseBody // [추가]
    public ResponseEntity<JsonNode> issueBillingKey(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/authorizations/issue");

        if (!response.has("error")) {
            billingKeyMap.put(requestData.get("customerKey").asText(), response.get("billingKey").asText());
        }

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @GetMapping("/callback-auth")
    @ResponseBody // [추가]
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

    @PostMapping(value = "/confirm/brandpay", consumes = "application/json")
    @ResponseBody // [추가]
    public ResponseEntity<JsonNode> confirmBrandpay(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String url = "https://api.tosspayments.com/v1/brandpay/payments/confirm";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // --- [2. View 메서드] : HTML 파일을 보여주는 역할 ---

    @GetMapping("/")
    public String index() {
        // [수정] 템플릿 엔진(Thymeleaf)이 아닌 정적 파일(static)로 포워딩
        return "forward:/widget/checkout.html";
    }

    @GetMapping("/fail")
    public String failPayment(HttpServletRequest request, Model model) {
        model.addAttribute("code", request.getParameter("code"));
        model.addAttribute("message", request.getParameter("message"));
        return "forward:/fail.html"; // fail.html도 static에 있다면 forward 사용
    }

    // --- [3. 유틸리티 메서드] ---
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