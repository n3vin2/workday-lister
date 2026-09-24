package io.github.n3vin2.workdaylister;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for backend integration tests: the full Spring context against a Testcontainers
 * MySQL, with a WireMock server standing in for every Workday Career Site, request pacing set
 * to zero, and the clock pinned to {@link PinnedClockConfig#PINNED_NOW}.
 *
 * <p>Tests drive the application only through its HTTP API ({@link #api}) and the Workday stub
 * ({@link #workday}). The MySQL container and the stub server are started once per JVM and shared
 * across test classes so Spring's context cache stays valid.
 *
 * <p>Every test starts with an empty Roster and an idle scraper. Because a successful upload starts
 * a Scrape Run on a background thread, the harness waits for {@code GET /api/runs/current} to
 * report no active run before the next test resets the stub and empties the Roster. Until a test
 * says otherwise, every Career Site on the stub is empty (a page with no postings), so a run always
 * finishes.
 *
 * <p>To observe a run in the middle of a Company, a test gives that Company's stub the
 * {@link #HOLD} transformer and calls {@link #holdResponses()}: the stub then answers only once
 * the test calls {@link #releaseHeldResponses()}. No test depends on a delay racing a deadline.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scraper.pacing-interval=0ms")
@Import(PinnedClockConfig.class)
public abstract class IntegrationHarness {

    /**
     * Holds every response of a stub that names it until the test releases it. Registered on the
     * stub server; stubs opt in with {@code withTransformers(HOLD)}.
     */
    public static final class HoldResponse implements ResponseDefinitionTransformerV2 {

        private static volatile CountDownLatch gate = new CountDownLatch(0);

        @Override
        public ResponseDefinition transform(ServeEvent serveEvent) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return serveEvent.getResponseDefinition();
        }

        @Override
        public String getName() {
            return HOLD;
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }

    /** The name a stub gives {@code withTransformers} to have its responses held. */
    protected static final String HOLD = "hold";

    /** A Career Site with no postings, in the shape Workday's jobs endpoint answers with. */
    protected static final String EMPTY_JOBS_PAGE =
            "{\"total\":0,\"jobPostings\":[],\"userAuthenticated\":false}";

    /** Lower priority than WireMock's default of 5, so a test's own stubs win over the fallback. */
    private static final int FALLBACK_PRIORITY = 10;

    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    /**
     * Stands in for Workday Career Sites; the Workday client's scheme and host point here. Recorded
     * responses live under {@code src/test/resources/wiremock/__files}. Plain-text HTTP/2 is off
     * because the JDK HTTP client's h2c upgrade makes WireMock's Jetty drop the connection; the real
     * Career Sites are reached over TLS, where negotiation works.
     */
    protected static final WireMockServer workday =
            new WireMockServer(
                    WireMockConfiguration.options()
                            .dynamicPort()
                            .usingFilesUnderClasspath("wiremock")
                            .http2PlainDisabled(true)
                            .extensions(new HoldResponse()));

    static {
        MYSQL.start();
        workday.start();
    }

    @DynamicPropertySource
    static void pointWorkdayClientAtStub(DynamicPropertyRegistry registry) {
        registry.add("workday.client.scheme", () -> "http");
        registry.add("workday.client.host", () -> "localhost:" + workday.port());
    }

    @Autowired
    protected TestRestTemplate api;

    @BeforeEach
    void startFromAnEmptyRosterAndAnIdleScraper() {
        releaseHeldResponses();
        awaitIdle();
        workday.resetAll();
        workday.stubFor(
                post(urlMatching("/wday/cxs/.*/jobs"))
                        .atPriority(FALLBACK_PRIORITY)
                        .willReturn(okJson(EMPTY_JOBS_PAGE)));
        uploadRoster("company,url\n");
    }

    /** Uploads the text as a {@code roster.csv} multipart file to {@code POST /api/roster}. */
    protected ResponseEntity<JsonNode> uploadRoster(String csv) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(
                "file",
                new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "roster.csv";
                    }
                });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return api.postForEntity("/api/roster", new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** {@code POST /api/runs}: starts a Scrape Run over the current Roster. */
    protected ResponseEntity<JsonNode> startRun() {
        return api.postForEntity("/api/runs", null, JsonNode.class);
    }

    /** {@code GET /api/runs/current}: the active run's progress, or 204 when none is active. */
    protected ResponseEntity<JsonNode> currentRun() {
        return api.getForEntity("/api/runs/current", JsonNode.class);
    }

    /** {@code POST /api/runs/current/cancel}: asks the active run to stop. */
    protected ResponseEntity<JsonNode> cancelRun() {
        return api.postForEntity("/api/runs/current/cancel", null, JsonNode.class);
    }

    /**
     * From now on, a stub using {@link #HOLD} answers only once {@link #releaseHeldResponses()} is
     * called.
     */
    protected static void holdResponses() {
        HoldResponse.gate = new CountDownLatch(1);
    }

    /** Lets every held response through. */
    protected static void releaseHeldResponses() {
        HoldResponse.gate.countDown();
    }

    /** Polls {@code GET /api/runs/{id}} until the run is no longer running. */
    protected void awaitRunFinished(long runId) {
        await().atMost(RUN_TIMEOUT)
                .untilAsserted(
                        () ->
                                assertThat(run(runId).path("status").asText())
                                        .isNotEqualTo("RUNNING"));
    }

    /** Polls {@code GET /api/runs/current} until no run is active. */
    protected void awaitIdle() {
        await().atMost(RUN_TIMEOUT)
                .untilAsserted(
                        () ->
                                assertThat(currentRun().getStatusCode())
                                        .isEqualTo(HttpStatus.NO_CONTENT));
    }

    /** {@code GET /api/runs/{id}}. */
    protected JsonNode run(long runId) {
        return api.getForObject("/api/runs/" + runId, JsonNode.class);
    }

    /** {@code GET /api/companies}: the Roster as the Roster screen lists it. */
    protected JsonNode companies() {
        return api.getForObject("/api/companies", JsonNode.class);
    }
}
