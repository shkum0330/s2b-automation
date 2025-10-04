package com.backend.domain.generation.dto;

import lombok.Data;

@Data
public class GenerateNonElectronicResponse {
    private String productName;
    private String specification;
    private String manufacturer;
    private String countryOfOrigin;
}
