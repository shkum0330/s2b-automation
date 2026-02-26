package com.backend.domain.generation.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.backend.domain.generation.dto.GenerateElectronicRequest;
import com.backend.domain.generation.dto.GenerateElectronicResponse;
import com.backend.domain.generation.service.AiProviderService;
import com.backend.domain.generation.service.ScrapingService;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.service.MemberService;
import com.backend.global.util.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationServiceImplLatencyTest {

    private static final Pattern ELAPSED_PATTERN = Pattern.compile("elapsedMs=(\\d+)");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("totalElapsedMs=(\\d+)");

    private MockWebServer mockWebServer;
    private ExecutorService taskExecutor;
    private ListAppender<ILoggingEvent> generationAppender;
    private ListAppender<ILoggingEvent> aiAppender;
    private GenerationServiceImpl generationService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(new TestAiDispatcher());
        mockWebServer.start();

        PromptBuilder promptBuilder = new DeterministicPromptBuilder();
        ObjectMapper objectMapper = new ObjectMapper();
        WebClient webClient = WebClient.builder().build();
        AiProviderService aiProviderService = new TestAiProviderService(
                promptBuilder,
                objectMapper,
                webClient,
                mockWebServer.url("/v1/generation").toString()
        );

        ScrapingService scrapingService = mock(ScrapingService.class);
        when(scrapingService.findG2bClassificationNumber(anyString())).thenAnswer(invocation -> {
            TimeUnit.MILLISECONDS.sleep(1400);
            return Optional.of("43211503");
        });
        when(scrapingService.findCountryOfOrigin(anyString())).thenAnswer(invocation -> {
            TimeUnit.MILLISECONDS.sleep(1300);
            return Optional.of("KOREA");
        });

        MemberService memberService = mock(MemberService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        taskExecutor = Executors.newFixedThreadPool(4);
        generationService = new GenerationServiceImpl(
                aiProviderService,
                scrapingService,
                memberService,
                taskExecutor,
                eventPublisher
        );

        generationAppender = attachAppender(GenerationServiceImpl.class);
        aiAppender = attachAppender(AbstractGenerationService.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        detachAppender(GenerationServiceImpl.class, generationAppender);
        detachAppender(AbstractGenerationService.class, aiAppender);
        taskExecutor.shutdownNow();
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("두 번의 생성 요청 지연 시간을 수집해 병목 구간을 확인한다")
    void collectElapsedMetricsAcrossTwoCalls() {
        CallMetrics firstRun = runAndCollect("MODEL-OK");
        CallMetrics secondRun = runAndCollect("MODEL-RETRY");

        assertThat(firstRun.retryElapsedMs).isZero();
        assertThat(secondRun.retryElapsedMs).isPositive();
        assertThat(secondRun.totalElapsedMs).isGreaterThan(firstRun.totalElapsedMs);
        assertThat(secondRun.mainAiElapsedMs).isGreaterThan(firstRun.mainAiElapsedMs);

        System.out.printf(
                "[LATENCY] run=1 total=%d g2b=%d country=%d certAi=%d mainAi=%d retry=%d bottleneck=%s%n",
                firstRun.totalElapsedMs,
                firstRun.g2bElapsedMs,
                firstRun.countryElapsedMs,
                firstRun.certAiElapsedMs,
                firstRun.mainAiElapsedMs,
                firstRun.retryElapsedMs,
                firstRun.bottleneckStage()
        );
        System.out.printf(
                "[LATENCY] run=2 total=%d g2b=%d country=%d certAi=%d mainAi=%d retry=%d bottleneck=%s%n",
                secondRun.totalElapsedMs,
                secondRun.g2bElapsedMs,
                secondRun.countryElapsedMs,
                secondRun.certAiElapsedMs,
                secondRun.mainAiElapsedMs,
                secondRun.retryElapsedMs,
                secondRun.bottleneckStage()
        );
    }

    private CallMetrics runAndCollect(String modelName) {
        generationAppender.list.clear();
        aiAppender.list.clear();

        GenerateElectronicRequest request = new GenerateElectronicRequest();
        request.setModelName(modelName);
        request.setSpecExample("Voltage: 220V");
        request.setProductNameExample("Sample Product");

        Member member = Member.createForToken(1L, "perf@example.com", Role.PLAN_30K);
        GenerateElectronicResponse response = generationService.generateSpec(request, member).join();
        assertThat(response.getModelName()).isEqualTo(modelName);

        List<String> generationLogs = generationAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        List<String> aiLogs = aiAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        return CallMetrics.from(generationLogs, aiLogs);
    }

    private static ListAppender<ILoggingEvent> attachAppender(Class<?> targetClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(targetClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(Class<?> targetClass, ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        Logger logger = (Logger) LoggerFactory.getLogger(targetClass);
        logger.detachAppender(appender);
        appender.stop();
    }

    private record CallMetrics(
            long totalElapsedMs,
            long g2bElapsedMs,
            long countryElapsedMs,
            long certAiElapsedMs,
            long mainAiElapsedMs,
            long retryElapsedMs
    ) {
        static CallMetrics from(List<String> generationLogs, List<String> aiLogs) {
            long totalElapsed = findRequired(generationLogs, log -> log.contains("totalElapsedMs="), TOTAL_PATTERN);
            long g2bElapsed = findRequired(generationLogs, log -> log.contains("G2B") && log.contains("elapsedMs="), ELAPSED_PATTERN);
            long countryElapsed = findRequired(
                    generationLogs,
                    log -> log.contains("found=") && !log.contains("G2B") && log.contains("elapsedMs="),
                    ELAPSED_PATTERN
            );
            long certAiElapsed = findMaxRequired(
                    aiLogs,
                    log -> log.contains("responseType=CertificationResponse") && log.contains("elapsedMs="),
                    ELAPSED_PATTERN
            );
            long mainAiElapsed = findMaxRequired(
                    aiLogs,
                    log -> log.contains("responseType=GenerateElectronicResponse") && log.contains("elapsedMs="),
                    ELAPSED_PATTERN
            );
            long retryElapsed = findOptional(
                    aiLogs,
                    log -> log.contains("responseType=GenerateElectronicResponse") && log.contains("retry="),
                    ELAPSED_PATTERN
            );

            return new CallMetrics(totalElapsed, g2bElapsed, countryElapsed, certAiElapsed, mainAiElapsed, retryElapsed);
        }

        String bottleneckStage() {
            long max = Math.max(Math.max(g2bElapsedMs, countryElapsedMs), Math.max(certAiElapsedMs, mainAiElapsedMs));
            if (max == mainAiElapsedMs) {
                return "main-spec-ai";
            }
            if (max == certAiElapsedMs) {
                return "certification-ai";
            }
            if (max == g2bElapsedMs) {
                return "scraping-g2b";
            }
            return "scraping-country";
        }
    }

    private static long findRequired(List<String> logs, java.util.function.Predicate<String> predicate, Pattern pattern) {
        return logs.stream()
                .filter(predicate)
                .map(pattern::matcher)
                .filter(Matcher::find)
                .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                .findFirst()
                .orElse(0L);
    }

    private static long findMaxRequired(List<String> logs, java.util.function.Predicate<String> predicate, Pattern pattern) {
        return logs.stream()
                .filter(predicate)
                .map(pattern::matcher)
                .filter(Matcher::find)
                .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                .max()
                .orElse(0L);
    }

    private static long findOptional(List<String> logs, java.util.function.Predicate<String> predicate, Pattern pattern) {
        return logs.stream()
                .filter(predicate)
                .map(pattern::matcher)
                .filter(Matcher::find)
                .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                .findFirst()
                .orElse(0L);
    }

    private static final class DeterministicPromptBuilder extends PromptBuilder {
        @Override
        public String buildProductSpecPrompt(String model, String specExample, String productNameExample) {
            return "MAIN:" + model;
        }

        @Override
        public String buildCertificationPrompt(String model) {
            return "CERT:" + model;
        }
    }

    private static final class TestAiProviderService extends AbstractGenerationService {

        private final String apiUrl;

        private TestAiProviderService(
                PromptBuilder promptBuilder,
                ObjectMapper objectMapper,
                WebClient webClient,
                String apiUrl
        ) {
            super(promptBuilder, objectMapper, webClient);
            this.apiUrl = apiUrl;
        }

        @Override
        protected String getApiUrl() {
            return apiUrl;
        }

        @Override
        protected HttpEntity<Object> createRequestEntity(String prompt) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(Map.of("prompt", prompt), headers);
        }

        @Override
        protected String extractTextFromResponse(String jsonResponse) throws Exception {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode text = root.path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new IllegalStateException("text field missing.");
            }
            return text.asText();
        }
    }

    private static final class TestAiDispatcher extends Dispatcher {

        private final AtomicBoolean mainRetryTriggered = new AtomicBoolean(false);

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String body = request.getBody().readUtf8();

            if (body.contains("MAIN:MODEL-RETRY") && mainRetryTriggered.compareAndSet(false, true)) {
                return new MockResponse()
                        .setResponseCode(500)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("{\"error\":\"temporary\"}")
                        .setBodyDelay(120, TimeUnit.MILLISECONDS);
            }

            if (body.contains("CERT:")) {
                String certJson = "{\"katsCertificationNumber\":\"KATS-TEST\",\"kcCertificationNumber\":\"KC-TEST\"}";
                return successResponse(certJson, 900);
            }

            if (body.contains("MAIN:")) {
                String modelName = body.contains("MODEL-RETRY") ? "MODEL-RETRY" : "MODEL-OK";
                String mainJson = "{\"productName\":\"TEST_PRODUCT\",\"specification\":\"Voltage: 220V\",\"modelName\":\"" + modelName
                        + "\",\"manufacturer\":\"TEST\",\"countryOfOrigin\":\"\"}";
                return successResponse(mainJson, 1700);
            }

            return new MockResponse()
                    .setResponseCode(400)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\":\"unknown request\"}");
        }

        private static MockResponse successResponse(String innerJson, long delayMillis) {
            String body = "{\"text\":\"" + escapeForJson(innerJson) + "\"}";
            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(body)
                    .setBodyDelay(delayMillis, TimeUnit.MILLISECONDS);
        }

        private static String escapeForJson(String value) {
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
}

