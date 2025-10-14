package com.backend.domain.generation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateNonElectronicRequest {
    @NotBlank(message = "물품명은 필수 입력값입니다.")
    private String productName;
    @NotBlank(message = "규격 예시는 필수 입력값입니다.")
    private String specExample;
}