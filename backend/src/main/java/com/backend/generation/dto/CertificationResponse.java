package com.backend.generation.dto;

import lombok.Data;

@Data
public class CertificationResponse {
    private String katsCertificationNumber; // 국가기술표준원 인증번호
    private String kcCertificationNumber;   // KC 전파적합성인증번호
}
