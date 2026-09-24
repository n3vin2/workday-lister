package io.github.n3vin2.workdaylister;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * a Scrape Run on a background thread, the harness remembers the last run it started and waits for
 * it to finish before the next test resets the stub and empties the Roster. Until a test says
 * otherwise, every Career Site on the stub is empty (a page with no postings), so a run always
 * finishes.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scraper.pacing-interval=0ms")
@Import(PinnedClockConfig.class)
public abstract class IntegrationHarness {

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
                            .http2PlainDisabled(true));

    private static Long lastStartedRun;

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
        if (lastStartedRun != null) {
            awaitRunFinished(lastStartedRun);
            lastStartedRun = null;
        }
        workday.resetAll();
        workday.stubFor(
                post(urlMatching("/wday/cxs/.*/jobs"))
                        .atPriority(FALLBACK_PRIORITY)
                        .willReturn(okJson(EMPTY_JOBS_PAGE)));
        uploadRoster("company,url\n");
    }

    /**
     * Uploads the given text as a {@code roster.csv} multipart file to {@code POST /api/roster} and
     * remembers the Scrape Run it started, if any.
     */
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
        ResponseEntity<JsonNode> response =
                api.postForEntity("/api/roster", new HttpEntity<>(body, headers), JsonNode.class);
        rememberRun(response);
        return response;
    }

    /** {@code POST /api/runs}: starts a Scrape Run over the current Roster and remembers it. */
    protected ResponseEntity<JsonNode> startRun() {
        ResponseEntity<JsonNode> response = api.postForEntity("/api/runs", null, JsonNode.class);
        rememberRun(response);
        return response;
    }

    /** Polls {@code GET /api/runs/{id}} until the run is no longer running. */
    protected void awaitRunFinished(long runId) {
        await().atMost(RUN_TIMEOUT)
                .untilAsserted(
                        () ->
                                assertThat(run(runId).path("status").asText())
                                        .isNotEqualTo("RUNNING"));
    }

    /** {@code GET /api/runs/{id}}. */
    protected JsonNode run(long runId) {
        return api.getForObject("/api/runs/" + runId, JsonNode.class);
    }

    /** {@code GET /api/companies}: the Roster as the Roster screen lists it. */
    protected JsonNode companies() {
        return api.getForObject("/api/companies", JsonNode.class);
    }

    private static void rememberRun(ResponseEntity<JsonNode> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return;
        }
        JsonNode body = response.getBody();
        JsonNode run = body.has("run") ? body.path("run") : body;
        if (run.hasNonNull("id")) {
            lastStartedRun = run.path("id").asLong();
        }
    }
}
