package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import io.github.n3vin2.workdaylister.PinnedClockConfig;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Scrape Runs through {@code POST /api/roster}, {@code POST /api/runs}, {@code GET /api/runs/{id}},
 * {@code GET /api/companies} and {@code GET /api/companies/{id}}, against a stubbed Workday.
 */
class ScrapeRunTest extends IntegrationHarness {

    private static final String ACME_JOBS = "/wday/cxs/acme/Careers/jobs";
    private static final String BETA_JOBS = "/wday/cxs/beta/Jobs/jobs";
    private static final String NVIDIA_JOBS = "/wday/cxs/nvidia/NVIDIAExternalCareerSite/jobs";

    /** Workday's cap on the postings one Career Site lists (ADR-0001). */
    private static final int CAP = 2000;
    private static final int PAGE = 20;

    private static final String ACME_ONLY =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            """;
    private static final String NVIDIA_ONLY =
            """
            company,url
            NVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite
            """;
    private static final String TWO_COMPANIES =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            Beta,https://beta.wd1.myworkdayjobs.com/Jobs
            """;

    private static final String PINNED_NOW = PinnedClockConfig.PINNED_NOW.toString();

    private static final ObjectMapper json = new ObjectMapper();

    @Test
    void aSuccessfulUploadStartsARunThatLeavesEachCompanySucceededWithItsOpenCount() {
        stubJobs(ACME_JOBS, 0, jobsPage(2, 0, 2));

        ResponseEntity<JsonNode> upload = uploadRoster(TWO_COMPANIES);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode run = upload.getBody().path("run");
        assertThat(run.path("status").asText()).isEqualTo("RUNNING");
        awaitRunFinished(run.path("id").asLong());
        JsonNode roster = companies();
        assertThat(texts(roster, "name")).containsExactly("Acme", "Beta");
        assertThat(texts(roster, "status")).containsOnly("SUCCEEDED");
        assertThat(ints(roster, "openCount")).containsExactly(2, 0);
        assertThat(texts(roster, "lastScrapedAt")).containsOnly(PINNED_NOW);
        assertThat(booleans(roster, "truncated")).containsOnly(false);
    }

    @Test
    void listRequestsCarryWorkdaysHeadersAndPageByTwentyUntilTheFirstPagesTotalIsReached() {
        stubJobs(ACME_JOBS, 0, jobsPage(45, 0, 20));
        stubJobs(ACME_JOBS, 20, jobsPage(0, 20, 20));
        stubJobs(ACME_JOBS, 40, jobsPage(0, 40, 5));

        uploadAndAwaitRun(ACME_ONLY);

        assertThat(ints(companies(), "openCount")).containsExactly(45);
        List<LoggedRequest> requests = workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)));
        assertThat(requests)
                .allSatisfy(
                        request -> {
                            assertThat(request.getHeader("Content-Type"))
                                    .isEqualTo("application/json");
                            assertThat(request.getHeader("Accept-Language")).isEqualTo("en-US");
                            assertThat(body(request).path("limit").asInt()).isEqualTo(20);
                        });
        assertThat(offsets(requests)).containsExactly(0, 20, 40);
    }

    @Test
    void pagingStopsAtAShortPageEvenWhenTheTotalPromisesMore() {
        stubJobs(ACME_JOBS, 0, jobsPage(45, 0, 20));
        stubJobs(ACME_JOBS, 20, jobsPage(0, 20, 15));

        uploadAndAwaitRun(ACME_ONLY);

        assertThat(ints(companies(), "openCount")).containsExactly(35);
        assertThat(offsets(workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)))))
                .containsExactly(0, 20);
    }

    @Test
    void aCareerSiteReportingWorkdaysCapOfTwoThousandIsFlaggedTruncated() {
        stubJobsFromRecording(NVIDIA_JOBS, 0, "workday/nvidia-jobs-page-1.json");
        stubJobsFromRecording(NVIDIA_JOBS, PAGE, "workday/nvidia-jobs-page-2.json");
        for (int offset = 2 * PAGE; offset < CAP; offset += PAGE) {
            stubJobs(NVIDIA_JOBS, offset, jobsPage(0, offset, PAGE));
        }

        long runId = uploadAndAwaitRun(NVIDIA_ONLY);

        JsonNode roster = companies();
        assertThat(ints(roster, "openCount")).containsExactly(CAP);
        assertThat(booleans(roster, "truncated")).containsExactly(true);
        JsonNode outcome = run(runId).path("outcomes").get(0);
        assertThat(outcome.path("postingsSeen").asInt()).isEqualTo(CAP);
        assertThat(outcome.path("truncated").asBoolean()).isTrue();
        assertThat(workday.findAll(postRequestedFor(urlEqualTo(NVIDIA_JOBS)))).hasSize(CAP / PAGE);
    }

    @Test
    void pagingStopsAtTwoThousandEvenWhenTheTotalPromisesMore() {
        for (int offset = 0; offset < CAP; offset += PAGE) {
            stubJobs(ACME_JOBS, offset, jobsPage(99_999, offset, PAGE));
        }

        uploadAndAwaitRun(ACME_ONLY);

        JsonNode roster = companies();
        assertThat(ints(roster, "openCount")).containsExactly(CAP);
        assertThat(booleans(roster, "truncated")).containsExactly(true);
        List<Integer> offsets = offsets(workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS))));
        assertThat(offsets).hasSize(CAP / PAGE).endsWith(CAP - PAGE);
    }

    @Test
    void theCompanyEndpointReturnsTheHeaderAndEveryOpenPostingWithItsWorkdayUrl() {
        stubJobs(
                ACME_JOBS,
                0,
                """
                {"total":2,"jobPostings":[
                  {"title":"Zebra Keeper","externalPath":"/job/Regina-SK/Zebra-Keeper_R2",
                   "locationsText":"Regina, SK","postedOn":"Posted Today","bulletFields":["R2"]},
                  {"title":"Accountant","externalPath":"/job/Saskatoon-SK/Accountant_R1",
                   "locationsText":"Saskatoon, SK","postedOn":"Posted 30+ Days Ago","bulletFields":["R1"]}
                ],"userAuthenticated":false}
                """);
        long runId = uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();

        ResponseEntity<JsonNode> response =
                api.getForEntity("/api/companies/" + acmeId, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode company = response.getBody();
        assertThat(company.path("id").asLong()).isEqualTo(acmeId);
        assertThat(company.path("name").asText()).isEqualTo("Acme");
        assertThat(company.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(company.path("lastScrapedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(company.path("openCount").asInt()).isEqualTo(2);
        assertThat(company.path("truncated").asBoolean()).isFalse();
        JsonNode postings = company.path("postings");
        assertThat(texts(postings, "title")).containsExactly("Accountant", "Zebra Keeper");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2");
        assertThat(texts(postings, "locationText")).containsExactly("Saskatoon, SK", "Regina, SK");
        assertThat(texts(postings, "postedOnLabel"))
                .containsExactly("Posted 30+ Days Ago", "Posted Today");
        assertThat(texts(postings, "state")).containsOnly("OPEN");
        assertThat(texts(postings, "publicUrl"))
                .containsExactly(
                        "https://acme.wd1.myworkdayjobs.com/Careers/job/Saskatoon-SK/Accountant_R1",
                        "https://acme.wd1.myworkdayjobs.com/Careers/job/Regina-SK/Zebra-Keeper_R2");
        assertThat(longs(postings, "firstSeenRunId")).containsOnly(runId);
        assertThat(longs(postings, "lastSeenRunId")).containsOnly(runId);
    }

    @Test
    void aLaterRunInsertsUnseenPostingsAndRefreshesEveryPostingItSees() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(
                        listing("Accountant", "/job/Saskatoon-SK/Accountant_R1", "Saskatoon, SK"),
                        listing("Zebra Keeper", "/job/Regina-SK/Zebra-Keeper_R2", "Regina, SK")));
        long firstRun = uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        stubJobs(
                ACME_JOBS,
                0,
                postings(
                        listing(
                                "Senior Zebra Keeper",
                                "/job/Moose-Jaw-SK/Senior-Zebra-Keeper_R2",
                                "Moose Jaw, SK"),
                        listing("Clerk", "/job/Regina-SK/Clerk_R3", "Regina, SK")));

        ResponseEntity<JsonNode> started = startRun();

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long secondRun = started.getBody().path("id").asLong();
        awaitRunFinished(secondRun);
        // The Accountant is Closed after the second run; it is asked for so its Last Seen shows.
        JsonNode postings = company(acmeId, true).path("postings");
        assertThat(texts(postings, "title"))
                .containsExactly("Accountant", "Clerk", "Senior Zebra Keeper");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R3", "R2");
        assertThat(texts(postings, "locationText"))
                .containsExactly("Saskatoon, SK", "Regina, SK", "Moose Jaw, SK");
        assertThat(longs(postings, "firstSeenRunId"))
                .containsExactly(firstRun, secondRun, firstRun);
        assertThat(longs(postings, "lastSeenRunId"))
                .containsExactly(firstRun, secondRun, secondRun);
        assertThat(run(secondRun).path("outcomes").get(0).path("postingsSeen").asInt())
                .isEqualTo(2);
    }

    @Test
    void scrapeNowStartsARunOverTheCurrentRosterAndRecordsEachCompanysOutcome() {
        stubJobs(ACME_JOBS, 0, jobsPage(3, 0, 3));
        uploadAndAwaitRun(TWO_COMPANIES);
        List<Long> companyIds = longs(companies(), "id");

        ResponseEntity<JsonNode> response = startRun();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode started = response.getBody();
        assertThat(started.path("status").asText()).isEqualTo("RUNNING");
        assertThat(started.path("startedAt").asText()).isEqualTo(PINNED_NOW);
        assertThat(started.path("finishedAt").isNull()).isTrue();
        assertThat(texts(started.path("outcomes"), "name")).containsExactly("Acme", "Beta");
        long runId = started.path("id").asLong();
        awaitRunFinished(runId);
        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(run.path("finishedAt").asText()).isEqualTo(PINNED_NOW);
        JsonNode outcomes = run.path("outcomes");
        assertThat(longs(outcomes, "companyId")).isEqualTo(companyIds);
        assertThat(texts(outcomes, "status")).containsOnly("SUCCEEDED");
        assertThat(ints(outcomes, "postingsSeen")).containsExactly(3, 0);
        assertThat(texts(outcomes, "startedAt")).containsOnly(PINNED_NOW);
        assertThat(texts(outcomes, "finishedAt")).containsOnly(PINNED_NOW);
        assertThat(booleans(outcomes, "truncated")).containsOnly(false);
    }

    @Test
    void scrapeNowWithAnEmptyRosterIsRefusedAndAnUnknownRunIsNotFound() {
        ResponseEntity<JsonNode> response = startRun();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().path("reason").asText())
                .isEqualTo("The Roster is empty; upload a CSV first");
        assertThat(api.getForEntity("/api/runs/999999", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void companiesAreScrapedOneAtATimeInNameOrder() {
        stubJobs(BETA_JOBS, 0, jobsPage(40, 0, 20));
        stubJobs(BETA_JOBS, 20, jobsPage(0, 20, 20));
        stubJobs(ACME_JOBS, 0, jobsPage(40, 0, 20));
        stubJobs(ACME_JOBS, 20, jobsPage(0, 20, 20));

        uploadAndAwaitRun(
                """
                company,url
                Beta,https://beta.wd1.myworkdayjobs.com/Jobs
                Acme,https://acme.wd1.myworkdayjobs.com/Careers
                """);

        List<String> served =
                workday.getAllServeEvents().stream()
                        .map(event -> event.getRequest().getUrl())
                        .toList()
                        .reversed();
        assertThat(served).containsExactly(ACME_JOBS, ACME_JOBS, BETA_JOBS, BETA_JOBS);
    }

    @Test
    void startingARunReturnsAtOnceAndTheRosterShowsTheCompanyBeingScraped() {
        workday.stubFor(
                post(urlEqualTo(ACME_JOBS))
                        .willReturn(okJson(jobsPage(1, 0, 1)).withTransformers(HOLD)));
        holdResponses();

        ResponseEntity<JsonNode> upload = uploadRoster(TWO_COMPANIES);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().untilAsserted(
                () ->
                        assertThat(texts(companies(), "status"))
                                .containsExactly("IN_PROGRESS", "NEVER_SCRAPED"));
        releaseHeldResponses();
        awaitRunFinished(upload.getBody().path("run").path("id").asLong());
        assertThat(texts(companies(), "status")).containsOnly("SUCCEEDED");
    }

    @Test
    void aCompanyThatIsNotInTheRosterIsNotFound() {
        ResponseEntity<JsonNode> response =
                api.getForEntity("/api/companies/999999", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Stubs one page with a response recorded from a real Career Site; the fixtures' README says
     * when and how it was captured.
     */
    private static void stubJobsFromRecording(String jobsPath, int offset, String bodyFile) {
        workday.stubFor(
                post(urlEqualTo(jobsPath))
                        .withRequestBody(matchingJsonPath("$[?(@.offset == " + offset + ")]"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile(bodyFile)));
    }

    private static JsonNode body(LoggedRequest request) {
        try {
            return json.readTree(request.getBodyAsString());
        } catch (IOException e) {
            throw new AssertionError("request body is not JSON: " + request.getBodyAsString(), e);
        }
    }

    /** The offsets the stub was asked for, in the order it was asked. */
    private static List<Integer> offsets(List<LoggedRequest> requests) {
        return requests.stream().map(request -> body(request).path("offset").asInt()).toList();
    }
}
