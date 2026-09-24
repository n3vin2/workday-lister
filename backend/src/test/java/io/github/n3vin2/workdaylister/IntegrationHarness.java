package io.github.n3vin2.workdaylister;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
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
 * MySQL, with a WireMock server standing in for every Workday Career Site, request pacing and
 * retry backoff set to zero, and the clock pinned to {@link PinnedClockConfig#PINNED_NOW}.
 *
 * <p>Tests drive the application only through its HTTP API ({@link #api}) and the Workday stub
 * ({@link #workday}). The MySQL container and the stub server are started once per JVM and shared
 * across test classes so Spring's context cache stays valid.
 *
 * <p>Every test starts with an empty Roster and an idle scraper. Because a successful upload starts
 * a Scrape Run on a background thread, the harness waits for {@code GET /api/runs/current} to
 * report no active run after each test and again before the next resets the stub and empties the
 * Roster; waiting after as well means no run outlives its test, even when the next test class
 * boots a context of its own (a subclass with a {@code @TestPropertySource}) against the same
 * database and stub. Until a test says otherwise, every Career Site on the stub is empty (a page
 * with no postings) and every posting's detail reports {@link #FALLBACK_POSTING_DATE}, so a run
 * always finishes and nothing is today's by accident.
 *
 * <p>To observe a run in the middle of a Company, a test gives that Company's stub the
 * {@link #HOLD} transformer and calls {@link #holdResponses()}: the stub then answers only once
 * the test calls {@link #releaseHeldResponses()}. No test depends on a delay racing a deadline.
 *
 * <p>Tests build Workday bodies with {@link WorkdayPages} and stub them here: a jobs page by
 * offset ({@link #stubJobs}, {@link #stubJobsFromRecording}) and a posting's detail by path
 * ({@link #stubDetail}). {@link #texts}, {@link #longs}, {@link #ints} and {@link #booleans} read
 * one field out of every element of a JSON array.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"scraper.pacing-interval=0ms", "scraper.retry-backoff=0ms"})
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

    /**
     * The Posting Date every posting's detail reports until a test stubs it: far from
     * {@link PinnedClockConfig#PINNED_TODAY}, so a posting is today's only when a test says so.
     */
    protected static final LocalDate FALLBACK_POSTING_DATE = LocalDate.of(2000, 1, 1);

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
        workday.stubFor(
                get(urlMatching("/wday/cxs/.*/job/.*"))
                        .atPriority(FALLBACK_PRIORITY)
                        .willReturn(okJson(WorkdayPages.detail(FALLBACK_POSTING_DATE))));
        uploadRoster("company,url\n");
    }

    @AfterEach
    void leaveTheScraperIdle() {
        releaseHeldResponses();
        awaitIdle();
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

    /** {@code POST /api/companies/{id}/retry}: starts a Scrape Run over just that Company. */
    protected ResponseEntity<JsonNode> retryCompany(long companyId) {
        return api.postForEntity("/api/companies/" + companyId + "/retry", null, JsonNode.class);
    }

    /** Uploads the Roster, which starts a run, waits for that run to finish, and returns its id. */
    protected long uploadAndAwaitRun(String csv) {
        ResponseEntity<JsonNode> upload = uploadRoster(csv);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        long runId = upload.getBody().path("run").path("id").asLong();
        awaitRunFinished(runId);
        return runId;
    }

    /** "Scrape now" over the current Roster; waits for the run to finish and returns its id. */
    protected long startAndAwaitRun() {
        ResponseEntity<JsonNode> started = startRun();
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long runId = started.getBody().path("id").asLong();
        awaitRunFinished(runId);
        return runId;
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

    /**
     * {@code GET /api/companies/{id}?scope=...}: one Company with the postings of that scope,
     * {@code today} or {@code all}, the Open ones only.
     */
    protected JsonNode company(long companyId, String scope) {
        return company(companyId, scope, false);
    }

    /**
     * {@code GET /api/companies/{id}?scope=...&includeClosed=...}: one Company with the postings
     * of that scope, and the Closed ones among them too when asked.
     */
    protected JsonNode company(long companyId, String scope, boolean includeClosed) {
        String query = "?scope=" + scope + "&includeClosed=" + includeClosed;
        return api.getForObject("/api/companies/" + companyId + query, JsonNode.class);
    }

    /** Stubs one page of a Career Site's jobs endpoint, matched on the requested offset. */
    protected static void stubJobs(String jobsPath, int offset, String body) {
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .withRequestBody(matchingJsonPath("$[?(@.offset == " + offset + ")]"))
                        .willReturn(okJson(body)));
    }

    /**
     * Stubs one page with a response recorded from a real Career Site; the fixtures' README says
     * when and how it was captured.
     */
    protected static void stubJobsFromRecording(String jobsPath, int offset, String bodyFile) {
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .withRequestBody(matchingJsonPath("$[?(@.offset == " + offset + ")]"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile(bodyFile)));
    }

    /**
     * Stubs one posting's detail endpoint, {@code /wday/cxs/{tenant}/{site}{externalPath}}, with
     * the given body (see {@link WorkdayPages#detail}).
     */
    protected static void stubDetail(String detailPath, String body) {
        workday.stubFor(get(urlEqualTo(detailPath)).willReturn(okJson(body)));
    }

    /**
     * One field of every element of a JSON array, as text; a JSON null is a Java null and a missing
     * field reads as empty.
     */
    protected static List<String> texts(JsonNode array, String field) {
        return values(array, field, node -> node.isNull() ? null : node.asText());
    }

    /** One field of every element of a JSON array, as a long. */
    protected static List<Long> longs(JsonNode array, String field) {
        return values(array, field, JsonNode::asLong);
    }

    /** One field of every element of a JSON array, as an int. */
    protected static List<Integer> ints(JsonNode array, String field) {
        return values(array, field, JsonNode::asInt);
    }

    /** One field of every element of a JSON array, as a boolean. */
    protected static List<Boolean> booleans(JsonNode array, String field) {
        return values(array, field, JsonNode::asBoolean);
    }

    /**
     * The time between consecutive requests, in the order the stub received them. A lower bound on
     * a gap is the one timing a test may assert on: the client's wait guarantees it.
     */
    protected static List<Duration> gaps(List<LoggedRequest> requests) {
        List<Instant> received =
                requests.stream()
                        .map(request -> request.getLoggedDate().toInstant())
                        .sorted()
                        .toList();
        List<Duration> gaps = new ArrayList<>();
        for (int i = 1; i < received.size(); i++) {
            gaps.add(Duration.between(received.get(i - 1), received.get(i)));
        }
        return gaps;
    }

    private static <T> List<T> values(JsonNode array, String field, Function<JsonNode, T> read) {
        List<T> values = new ArrayList<>();
        array.forEach(node -> values.add(read.apply(node.path(field))));
        return values;
    }
}
