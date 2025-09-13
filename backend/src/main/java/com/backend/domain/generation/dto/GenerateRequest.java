package com.backend.domain.generation.dto;

import lombok.Data;

@Data
public class GenerateRequest {
    private String model;
    private String specExample;
    private String productNameExample;
}
