package com.backend.domain.generation.dto;

import lombok.Data;

@Data
public class GenerateResponse {
    private String productName;
    private String specification;
    private String modelName;
    private String katsCertificationNumber; // 국가기술표준원 인증번호
    private String kcCertificationNumber;   // KC 전파적합성인증번호
    private String manufacturer; // 제조사
    private String countryOfOrigin; // 원산지
//    private List<PriceInfo> priceList; // 가격 정보 리스트
    private String g2bClassificationNumber; // G2B 물품목록번호

    public void setCertificationNumber(CertificationResponse certificationResponse) {
        this.katsCertificationNumber = certificationResponse.getKatsCertificationNumber();
        this.kcCertificationNumber = certificationResponse.getKcCertificationNumber();
    }

}
