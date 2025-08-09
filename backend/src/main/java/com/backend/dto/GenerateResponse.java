package com.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GenerateResponse {
    private String productName;
    private String specification;
    private String modelName;
    private String katsCertificationNumber; // 국가기술표준원 인증번호
    private String kcCertificationNumber;   // KC 전파적합성인증번호
}
