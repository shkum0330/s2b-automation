package com.backend.domain.generation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateElectronicRequest {
    @NotBlank(message = "모델명은 필수 입력값입니다.")
    @JsonProperty("model")
    private String modelName;
    @NotBlank(message = "규격 예시는 필수 입력값입니다.")
    private String specExample;
    private String productNameExample;
}
