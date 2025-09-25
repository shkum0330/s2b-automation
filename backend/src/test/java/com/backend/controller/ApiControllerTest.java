package com.backend.controller;

import com.backend.domain.generation.dto.CertificationResponse;
import com.backend.domain.generation.dto.GenerateRequest;
import com.backend.domain.generation.dto.GenerateResponse;
import com.backend.domain.generation.controller.ApiController;
import com.backend.domain.generation.service.GenerationService;
import com.backend.domain.generation.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture; // CompletableFuture import
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@ExtendWith(RestDocumentationExtension.class)
@Import(ApiControllerTest.TestConfig.class)
class ApiControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GenerationService generationService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TaskService taskService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public GenerationService generationService() {
            return Mockito.mock(GenerationService.class);
        }

        @Bean
        public TaskService taskService() {
            return Mockito.mock(TaskService.class);
        }

        @Bean
        public Executor taskExecutor() {
            return Mockito.mock(Executor.class);
        }
    }

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext, RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("제품 사양 정보 생성 API")
    void generateSpecification() throws Exception {
        // given - 문서화를 위한 요청 및 응답 객체 설정
        GenerateRequest request = new GenerateRequest();
        request.setModel("AX033B310GBD");
        request.setSpecExample("32평형(106㎡) / UV살균 / 에너지효율 2등급 / 380×876×406mm");
        request.setProductNameExample("삼성전자 비스포크 큐브 Air 공기청정기");

        GenerateResponse response = new GenerateResponse();
        CertificationResponse certResponse = new CertificationResponse();
        certResponse.setKatsCertificationNumber("YU07266-22001");
        certResponse.setKcCertificationNumber("R-R-SEC-AIR2206");

        response.setProductName("삼성전자 블루스카이 3100 공기청정기");
        response.setSpecification("10평형(33㎡) / 집진(헤파)필터 / 초미세먼지제거 / 탈취 / 제균");
        response.setModelName("AX033B310GBD");
        response.setManufacturer("삼성전자(주)");
        response.setCountryOfOrigin("태국");
        response.setG2bClassificationNumber("24574852");
        response.setCertificationNumber(certResponse);

        // 서비스가 CompletableFuture를 반환하도록 Mocking
        when(generationService.generateSpec(any(), any(), any(),any())).thenReturn(CompletableFuture.completedFuture(response));

        // when & then & andDo(document)
        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/generate-spec")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("generate-spec",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("model").description("조사할 제품의 모델명"),
                                fieldWithPath("specExample").description("참고할 규격의 형식 예시"),
                                fieldWithPath("productNameExample").description("참고할 물품명의 형식 예시 (선택 사항)")
                        ),
                        responseFields(
                                fieldWithPath("productName").description("AI가 생성한 물품명"),
                                fieldWithPath("specification").description("AI가 생성한 규격"),
                                fieldWithPath("modelName").description("조사 대상 모델명 (입력값과 동일)"),
                                fieldWithPath("manufacturer").description("제조사"),
                                fieldWithPath("countryOfOrigin").description("원산지"),
                                fieldWithPath("g2bClassificationNumber").description("G2B 물품목록번호"),
                                fieldWithPath("katsCertificationNumber").description("국가기술표준원 전기안전인증번호"),
                                fieldWithPath("kcCertificationNumber").description("KC 전파적합성인증번호")
                        )
                ));
    }

    @Test
    @DisplayName("작업 취소 API")
    void cancelTask() throws Exception {
        String taskId = UUID.randomUUID().toString();
        when(taskService.cancelTask(anyString())).thenReturn(true);

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/cancel/{taskId}", taskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("cancel-task",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("taskId").description("취소할 작업의 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").description("작업 취소 성공 여부")
                        )
                ));
    }
}