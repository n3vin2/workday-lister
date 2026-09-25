package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import io.github.n3vin2.workdaylister.PinnedClockConfig;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Failure isolation and per-Company retry through {@code POST /api/roster}, {@code GET
 * /api/runs/{id}}, {@code GET /api/companies} and {@code POST /api/companies/{id}/retry}, against
 * a stubbed Workday. Retries are counted through the stub's request journal; the harness sets the
 * backoff to zero so a failing Company costs no waiting.
 */
class FailedCompanyTest extends IntegrationHarness {

    private static final String ACME_JOBS = "/wday/cxs/acme/Careers/jobs";
    private static final String BETA_JOBS = "/wday/cxs/beta/Jobs/jobs";
    private static final String GAMMA_JOBS = "/wday/cxs/gamma/Careers/jobs";

    private static final String ACME_ONLY =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            """;
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

    /** The configured retry count is 3, so a request that keeps failing is sent four times. */
    private static final int ATTEMPTS = 4;

    @Test
    void aCompanyWhoseListRequestFailsAfterTheLastRetryIsMarkedFailedAndTheRunContinues() {
        workday.stubFor(post(urlEqualTo(ACME_JOBS)).willReturn(serverError()));
        stubJobs(BETA_JOBS, 0, jobsPage(2, 0, 2));

        long runId = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();
        awaitRunFinished(runId);

        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("PARTIALLY_FAILED");
        assertThat(run.path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(run.path("done").asInt()).isEqualTo(2);
        JsonNode outcomes = run.path("outcomes");
        assertThat(texts(outcomes, "status")).containsExactly("FAILED", "SUCCEEDED");
        JsonNode acme = outcomes.get(0);
        assertThat(acme.path("errorMessage").asText())
                .isEqualTo("Career Site answered HTTP 500 after 3 retries");
        assertThat(acme.path("startedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(acme.path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(acme.path("postingsSeen").asInt()).isZero();
        assertThat(outcomes.get(1).path("errorMessage").isNull()).isTrue();
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("FAILED", "SUCCEEDED");
        assertThat(roster.get(0).path("errorMessage").asText())
                .isEqualTo("Career Site answered HTTP 500 after 3 retries");
        assertThat(roster.get(0).path("lastScrapedAt").isNull()).isTrue();
        assertThat(roster.get(1).path("errorMessage").isNull()).isTrue();
        assertThat(ints(roster, "openCount")).containsExactly(0, 2);
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)))).hasSize(ATTEMPTS);
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(BETA_JOBS)))).hasSize(1);
    }

    @Test
    void aThrottledRequestIsRetriedOnceRetryAfterAllowsAndTheCompanySucceeds() {
        stubFailingOnce(ACME_JOBS, aResponse().withStatus(429).withHeader("Retry-After", "1"));

        long runId = uploadAndAwaitRun(ACME_ONLY);

        assertThat(run(runId).path("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("SUCCEEDED");
        assertThat(roster.get(0).path("errorMessage").isNull()).isTrue();
        assertThat(ints(roster, "openCount")).containsExactly(1);
        List<LoggedRequest> requests = workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)));
        assertThat(requests).hasSize(2);
        assertThat(gaps(requests))
                .allSatisfy(gap -> assertThat(gap).isGreaterThanOrEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void aRetryAfterDateIsHonouredAgainstTheApplicationClock() {
        String oneSecondAfterNow =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        PinnedClockConfig.PINNED_NOW.plusSeconds(1).atZone(ZoneOffset.UTC));
        stubFailingOnce(
                ACME_JOBS, serviceUnavailable().withHeader("Retry-After", oneSecondAfterNow));

        long runId = uploadAndAwaitRun(ACME_ONLY);

        assertThat(run(runId).path("status").asText()).isEqualTo("SUCCEEDED");
        List<LoggedRequest> requests = workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)));
        assertThat(requests).hasSize(2);
        assertThat(gaps(requests))
                .allSatisfy(gap -> assertThat(gap).isGreaterThanOrEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void aDeadOrWrongPodCareerSiteIsMarkedFailedWithoutRetries() {
        workday.stubFor(
                post(urlEqualTo(ACME_JOBS))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        workday.stubFor(post(urlEqualTo(BETA_JOBS)).willReturn(notFound()));
        stubJobs(GAMMA_JOBS, 0, jobsPage(1, 0, 1));

        long runId = uploadAndAwaitRun(THREE_COMPANIES);

        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("PARTIALLY_FAILED");
        assertThat(texts(run.path("outcomes"), "status"))
                .containsExactly("FAILED", "FAILED", "SUCCEEDED");
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("FAILED", "FAILED", "SUCCEEDED");
        assertThat(roster.get(0).path("errorMessage").asText())
                .startsWith("Career Site unreachable: ");
        assertThat(roster.get(1).path("errorMessage").asText())
                .isEqualTo("Career Site answered HTTP 404");
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)))).hasSize(1);
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(BETA_JOBS)))).hasSize(1);
    }

    @Test
    void retryingAFailedCompanyRunsJustThatCompanyAndClearsItsFailure() {
        workday.stubFor(post(urlEqualTo(ACME_JOBS)).willReturn(serverError()));
        stubJobs(BETA_JOBS, 0, jobsPage(2, 0, 2));
        uploadAndAwaitRun(TWO_COMPANIES);
        long acmeId = companies().get(0).path("id").asLong();
        workday.resetAll();
        stubJobs(ACME_JOBS, 0, jobsPage(3, 0, 3));

        ResponseEntity<JsonNode> response = retryCompany(acmeId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode started = response.getBody();
        assertThat(started.path("status").asText()).isEqualTo("RUNNING");
        assertThat(started.path("total").asInt()).isEqualTo(1);
        assertThat(texts(started.path("outcomes"), "name")).containsExactly("Acme");
        long runId = started.path("id").asLong();
        awaitRunFinished(runId);
        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(texts(run.path("outcomes"), "status")).containsExactly("SUCCEEDED");
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("SUCCEEDED", "SUCCEEDED");
        assertThat(roster.get(0).path("errorMessage").isNull()).isTrue();
        assertThat(roster.get(0).path("lastScrapedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(ints(roster, "openCount")).containsExactly(3, 2);
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(BETA_JOBS)))).isEmpty();
    }

    @Test
    void retryWhileARunIsActiveIsRefusedAndAnUnknownCompanyIsNotFound() {
        workday.stubFor(
                post(urlEqualTo(ACME_JOBS))
                        .willReturn(okJson(jobsPage(1, 0, 1)).withTransformers(HOLD)));
        holdResponses();
        long runId = uploadRoster(TWO_COMPANIES).getBody().path("run").path("id").asLong();
        long betaId = companies().get(1).path("id").asLong();

        ResponseEntity<JsonNode> refused = retryCompany(betaId);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().path("reason").asText()).isEqualTo(RUN_IN_PROGRESS);

        releaseHeldResponses();
        awaitRunFinished(runId);

        assertThat(retryCompany(999_999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(texts(run(runId).path("outcomes"), "status")).containsOnly("SUCCEEDED");
    }

    /**
     * Stubs a Career Site's jobs endpoint to answer the given error once and a one-posting page
     * from then on.
     */
    private static void stubFailingOnce(String jobsPath, ResponseDefinitionBuilder error) {
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .inScenario(jobsPath)
                        .whenScenarioStateIs(STARTED)
                        .willReturn(error)
                        .willSetStateTo("recovered"));
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .inScenario(jobsPath)
                        .whenScenarioStateIs("recovered")
                        .willReturn(okJson(jobsPage(1, 0, 1))));
    }
}
