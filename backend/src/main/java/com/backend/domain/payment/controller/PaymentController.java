package com.backend.domain.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j; // [변경] Lombok Slf4j 임포트
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class PaymentController {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${toss.widget-secret-key}")
    private String widgetSecretKey;
    @Value("${toss.secret-key}")
    private String apiSecretKey;

    private final Map<String, String> billingKeyMap = new HashMap<>();

    @RequestMapping(value = {"/confirm/widget", "/confirm/payment"})
    public ResponseEntity<JsonNode> confirmPayment(HttpServletRequest request, @RequestBody String jsonBody) throws Exception {
        String secretKey = request.getRequestURI().contains("/confirm/payment") ? apiSecretKey : widgetSecretKey;
        JsonNode requestData = objectMapper.readTree(jsonBody); // 파싱
        JsonNode response = sendRequest(requestData, secretKey, "https://api.tosspayments.com/v1/payments/confirm");

        int statusCode = response.has("error") ? 400 : 200;
        return ResponseEntity.status(statusCode).body(response);
    }

    @RequestMapping(value = "/confirm-billing")
    public ResponseEntity<JsonNode> confirmBilling(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String billingKey = billingKeyMap.get(requestData.get("customerKey").asText());
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/" + billingKey);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/issue-billing-key")
    public ResponseEntity<JsonNode> issueBillingKey(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        JsonNode response = sendRequest(requestData, apiSecretKey, "https://api.tosspayments.com/v1/billing/authorizations/issue");

        if (!response.has("error")) {
            billingKeyMap.put(requestData.get("customerKey").asText(), response.get("billingKey").asText());
        }

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/callback-auth", method = RequestMethod.GET)
    public ResponseEntity<JsonNode> callbackAuth(@RequestParam String customerKey, @RequestParam String code) throws Exception {
        // ObjectNode를 사용하여 JSON 객체 생성
        ObjectNode requestData = objectMapper.createObjectNode();
        requestData.put("grantType", "AuthorizationCode");
        requestData.put("customerKey", customerKey);
        requestData.put("code", code);

        String url = "https://api.tosspayments.com/v1/brandpay/authorizations/access-token";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);

        // [변경] logger -> log
        log.info("Response Data: {}", response);

        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/confirm/brandpay", method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<JsonNode> confirmBrandpay(@RequestBody String jsonBody) throws Exception {
        JsonNode requestData = objectMapper.readTree(jsonBody);
        String url = "https://api.tosspayments.com/v1/brandpay/payments/confirm";
        JsonNode response = sendRequest(requestData, apiSecretKey, url);
        return ResponseEntity.status(response.has("error") ? 400 : 200).body(response);
    }

    // [핵심 변경] JSONObject 대신 JsonNode 사용
    private JsonNode sendRequest(JsonNode requestData, String secretKey, String urlString) throws IOException {
        HttpURLConnection connection = createConnection(secretKey, urlString);
        try (OutputStream os = connection.getOutputStream()) {
            // JsonNode -> String 변환
            os.write(objectMapper.writeValueAsBytes(requestData));
        }

        try (InputStream responseStream = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream();
             Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8)) {
            // Stream -> JsonNode 파싱
            return objectMapper.readTree(reader);
        } catch (Exception e) {
            // [변경] logger -> log
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

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index() {
        return "/widget/checkout";
    }

    @RequestMapping(value = "/fail", method = RequestMethod.GET)
    public String failPayment(HttpServletRequest request, Model model) {
        model.addAttribute("code", request.getParameter("code"));
        model.addAttribute("message", request.getParameter("message"));
        return "/fail";
    }
}