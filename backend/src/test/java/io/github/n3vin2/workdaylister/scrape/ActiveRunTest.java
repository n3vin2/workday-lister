package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import io.github.n3vin2.workdaylister.PinnedClockConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The active Scrape Run through {@code GET /api/runs/current} and
 * {@code POST /api/runs/current/cancel}, and the one-run-at-a-time rule on {@code POST /api/runs}
 * and {@code POST /api/roster}, against a stubbed Workday.
 */
class ActiveRunTest extends IntegrationHarness {

    private static final String ACME_JOBS = "/wday/cxs/acme/Careers/jobs";
    private static final String BETA_JOBS = "/wday/cxs/beta/Jobs/jobs";
    private static final String GAMMA_JOBS = "/wday/cxs/gamma/Careers/jobs";

    private static final String TWO_COMPANIES =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            Beta,https://beta.wd1.myworkdayjobs.com/Jobs
            """;
    private static final String THREE_COMPANIES =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            Beta,https://beta.wd1.myworkdayjobs.com/Jobs
            Gamma,https://gamma.wd1.myworkdayjobs.com/Careers
            """;

    private static final String PINNED_NOW = PinnedClockConfig.PINNED_NOW.toString();
    private static final String RUN_IN_PROGRESS =
            "A Scrape Run is in progress; wait for it to finish or cancel it";

    @Test
    void theCurrentRunReportsProgressWhileActiveAndNoContentWhenIdle() {
        stubJobs(ACME_JOBS, 0, jobsPage(3, 0, 3));
        holdJobs(BETA_JOBS, jobsPage(1, 0, 1));
        holdResponses();

        long runId = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();

        await().untilAsserted(
                () ->
                        assertThat(texts(currentRun().getBody().path("outcomes"), "status"))
                                .containsExactly("SUCCEEDED", "IN_PROGRESS"));
        ResponseEntity<JsonNode> current = currentRun();
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode run = current.getBody();
        assertThat(run.path("id").asLong()).isEqualTo(runId);
        assertThat(run.path("status").asText()).isEqualTo("RUNNING");
        assertThat(run.path("startedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(run.path("done").asInt()).isEqualTo(1);
        assertThat(run.path("total").asInt()).isEqualTo(2);
        assertThat(texts(run.path("outcomes"), "name")).containsExactly("Acme", "Beta");

        releaseHeldResponses();
        awaitIdle();

        assertThat(currentRun().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode finished = run(runId);
        assertThat(finished.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(finished.path("done").asInt()).isEqualTo(2);
        assertThat(finished.path("total").asInt()).isEqualTo(2);
    }

    @Test
    void scrapeNowWhileARunIsActiveIsRefusedUntilThatRunEnds() {
        holdJobs(ACME_JOBS, jobsPage(1, 0, 1));
        holdResponses();
        long firstRun = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();

        ResponseEntity<JsonNode> refused = startRun();

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().path("reason").asText()).isEqualTo(RUN_IN_PROGRESS);

        releaseHeldResponses();
        awaitIdle();
        ResponseEntity<JsonNode> started = startRun();

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(started.getBody().path("id").asLong()).isNotEqualTo(firstRun);
    }

    @Test
    void anUploadWhileARunIsActiveIsRefusedAndChangesNothing() {
        holdJobs(ACME_JOBS, jobsPage(1, 0, 1));
        holdResponses();
        long runId = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();

        ResponseEntity<JsonNode> refused =
                uploadRoster(
                        """
                        company,url
                        Gamma,https://gamma.wd1.myworkdayjobs.com/Careers
                        """);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().path("reason").asText()).isEqualTo(RUN_IN_PROGRESS);
        assertThat(texts(companies(), "name")).containsExactly("Acme", "Beta");

        releaseHeldResponses();
        awaitIdle();

        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(texts(run.path("outcomes"), "status")).containsExactly("SUCCEEDED", "SUCCEEDED");
        assertThat(texts(companies(), "name")).containsExactly("Acme", "Beta");
    }

    @Test
    void cancelBetweenCompaniesKeepsFinishedCompaniesAndCancelsTheRest() {
        stubJobs(ACME_JOBS, 0, jobsPage(3, 0, 3));
        holdJobs(BETA_JOBS, jobsPage(2, 0, 2));
        holdResponses();
        long runId = uploadRoster(THREE_COMPANIES).getBody().path("run").path("id").asLong();
        await().untilAsserted(
                () ->
                        assertThat(texts(currentRun().getBody().path("outcomes"), "status"))
                                .containsExactly("SUCCEEDED", "IN_PROGRESS", "QUEUED"));

        ResponseEntity<JsonNode> cancel = cancelRun();

        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(cancel.getBody().path("id").asLong()).isEqualTo(runId);

        releaseHeldResponses();
        awaitIdle();

        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(run.path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(run.path("done").asInt()).isEqualTo(3);
        JsonNode outcomes = run.path("outcomes");
        assertThat(texts(outcomes, "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED", "CANCELLED");
        assertThat(outcomes.get(2).path("startedAt").isNull()).isTrue();
        assertThat(outcomes.get(2).path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        JsonNode roster = companies();
        assertThat(texts(roster, "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED", "NEVER_SCRAPED");
        assertThat(ints(roster, "openCount")).containsExactly(3, 2, 0);
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(GAMMA_JOBS)))).isEmpty();
    }

    @Test
    void cancelBetweenPagesLeavesTheCurrentCompanyCancelledWithItsPostingsUntouched() {
        stubJobs(BETA_JOBS, 0, jobsPage(2, 0, 2));
        long firstRun = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();
        awaitRunFinished(firstRun);
        long betaId = companies().get(1).path("id").asLong();
        workday.resetAll();
        stubJobs(ACME_JOBS, 0, jobsPage(1, 0, 1));
        holdJobs(BETA_JOBS, jobsPage(40, 100, 20));
        holdResponses();
        long secondRun = startRun().getBody().path("id").asLong();
        await().untilAsserted(
                () ->
                        assertThat(texts(currentRun().getBody().path("outcomes"), "status"))
                                .containsExactly("SUCCEEDED", "IN_PROGRESS"));

        assertThat(cancelRun().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        releaseHeldResponses();
        awaitIdle();

        JsonNode run = run(secondRun);
        assertThat(run.path("status").asText()).isEqualTo("CANCELLED");
        JsonNode beta = run.path("outcomes").get(1);
        assertThat(beta.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(beta.path("startedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(beta.path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(beta.path("postingsSeen").asInt()).isEqualTo(0);
        JsonNode company = api.getForObject("/api/companies/" + betaId, JsonNode.class);
        assertThat(company.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(company.path("openCount").asInt()).isEqualTo(2);
        assertThat(texts(company.path("postings"), "requisitionId")).containsExactly("R0", "R1");
        assertThat(longs(company.path("postings"), "lastSeenRunId")).containsOnly(firstRun);
        assertThat(texts(companies(), "status")).containsExactly("SUCCEEDED", "CANCELLED");
        // The run stopped between pages: Beta's second page was never asked for.
        assertThat(
                        workday.findAll(
                                postRequestedFor(urlEqualTo(BETA_JOBS))
                                        .withRequestBody(
                                                matchingJsonPath("$[?(@.offset == 20)]"))))
                .isEmpty();
    }

    @Test
    void cancelWhenNoRunIsActiveIsNoContent() {
        ResponseEntity<JsonNode> response = cancelRun();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /** Stubs one page of a Career Site's jobs endpoint, matched on the requested offset. */
    private static void stubJobs(String jobsPath, int offset, String body) {
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .withRequestBody(matchingJsonPath("$[?(@.offset == " + offset + ")]"))
                        .willReturn(okJson(body)));
    }

    /** Stubs a Career Site's jobs endpoint to answer only once held responses are released. */
    private static void holdJobs(String jobsPath, String body) {
        workday.stubFor(post(urlEqualTo(jobsPath)).willReturn(okJson(body).withTransformers(HOLD)));
    }

    /**
     * A page in the shape of the recorded fixtures: {@code count} postings numbered from
     * {@code offset}, with requisition IDs {@code R<n>}, and the given {@code total}.
     */
    private static String jobsPage(int total, int offset, int count) {
        List<String> postings = new ArrayList<>();
        for (int n = offset; n < offset + count; n++) {
            postings.add(
                    """
                    {"title":"Engineer %d","externalPath":"/job/Regina-SK/Engineer-%d_R%d",\
                    "locationsText":"Regina, SK","postedOn":"Posted 30+ Days Ago",\
                    "bulletFields":["R%d"]}"""
                            .formatted(n, n, n, n));
        }
        return "{\"total\":%d,\"jobPostings\":[%s],\"userAuthenticated\":false}"
                .formatted(total, String.join(",", postings));
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    private static List<Long> longs(JsonNode array, String field) {
        List<Long> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asLong()));
        return values;
    }

    private static List<Integer> ints(JsonNode array, String field) {
        List<Integer> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asInt()));
        return values;
    }
}
