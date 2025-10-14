package com.backend.domain.generation.service;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.dto.GenerateNonElectronicResponse;

import java.util.concurrent.CompletableFuture;

public interface AiProviderService {
    CompletableFuture<GenerateElectronicResponse> fetchMainSpec(String model, String specExample, String productNameExample);
    CompletableFuture<CertificationResponse> fetchCertification(String model);
    CompletableFuture<GenerateNonElectronicResponse> fetchGeneralSpec(String productName, String specExample);
}
