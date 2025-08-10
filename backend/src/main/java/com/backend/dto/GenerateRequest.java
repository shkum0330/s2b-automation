package com.backend.dto;

import lombok.Data;

@Data
public class GenerateRequest {
    private String model;
    private String specExample;
    private String productNameExample;
}
