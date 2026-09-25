package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.github.n3vin2.workdaylister.PinnedClockConfig.PINNED_TODAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_LONG_AGO;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_TODAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_YESTERDAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.detail;
import static io.github.n3vin2.workdaylister.WorkdayPages.listing;
import static io.github.n3vin2.workdaylister.WorkdayPages.page;
import static io.github.n3vin2.workdaylister.WorkdayPages.postings;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Today's Postings (ADR-0002): which postings a Scrape Run fetches a detail for, the Posting Date
 * it stores, what it stores when a detail cannot be read, and how {@code GET /api/companies} and
 * {@code GET /api/companies/{id}?scope=...} report today's, against a stubbed Workday and a clock
 * pinned to {@link io.github.n3vin2.workdaylister.PinnedClockConfig#PINNED_TODAY}.
 */
class TodaysPostingsTest extends IntegrationHarness {

    private static final String ACME_SITE = "/wday/cxs/acme/Careers";
    private static final String ACME_JOBS = ACME_SITE + "/jobs";
    private static final String BETA_SITE = "/wday/cxs/beta/Jobs";
    private static final String BETA_JOBS = BETA_SITE + "/jobs";
    private static final String ZED_SITE = "/wday/cxs/zed/Careers";
    private static final String ZED_JOBS = ZED_SITE + "/jobs";

    private static final int PAGE = 20;

    private static final String ACME_ONLY =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            """;
    private static final String THREE_COMPANIES =
            """
            company,url
            Zed,https://zed.wd1.myworkdayjobs.com/Careers
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            Beta,https://beta.wd1.myworkdayjobs.com/Jobs
            """;

    private static final String ACCOUNTANT_PATH = "/job/Regina-SK/Accountant_R1";
    private static final String CLERK_PATH = "/job/Regina-SK/Clerk_R2";
    private static final String ENGINEER_PATH = "/job/Regina-SK/Engineer_R3";
    private static final String ZEBRA_KEEPER_PATH = "/job/Regina-SK/Zebra-Keeper_R4";

    @Test
    void detailIsFetchedOnlyForPostingsLabelledTodayOrYesterdayAndItsStartDateIsThePostingDate() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(
                        listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_YESTERDAY),
                        listing("Engineer", ENGINEER_PATH, "Regina, SK", "Posted 2 Days Ago"),
                        listing("Zebra Keeper", ZEBRA_KEEPER_PATH, "Regina, SK", POSTED_LONG_AGO)));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));
        stubDetail(ACME_SITE + CLERK_PATH, detail(PINNED_TODAY.minusDays(1)));

        uploadAndAwaitRun(ACME_ONLY);

        long acmeId = companies().get(0).path("id").asLong();
        JsonNode postings = company(acmeId, "all").path("postings");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2", "R3", "R4");
        assertThat(texts(postings, "postingDate"))
                .containsExactly("2026-09-21", "2026-09-20", null, null);
        List<LoggedRequest> details =
                workday.findAll(getRequestedFor(urlMatching(ACME_SITE + "/job/.*")));
        assertThat(details)
                .extracting(LoggedRequest::getUrl)
                .containsExactly(ACME_SITE + ACCOUNTANT_PATH, ACME_SITE + CLERK_PATH);
        assertThat(details)
                .allSatisfy(
                        request ->
                                assertThat(request.getHeader("Accept-Language"))
                                        .isEqualTo("en-US"));
    }

    @Test
    void todaysPostingsAreTheOpenPostingsWhosePostingDateIsTodayInReginaWhateverTheLabelSays() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(
                        listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_YESTERDAY),
                        listing("Engineer", ENGINEER_PATH, "Regina, SK", POSTED_LONG_AGO)));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY.minusDays(1)));
        stubDetail(ACME_SITE + CLERK_PATH, detail(PINNED_TODAY));
        uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();

        JsonNode byDefault = api.getForObject("/api/companies/" + acmeId, JsonNode.class);

        assertThat(texts(byDefault.path("postings"), "requisitionId")).containsExactly("R2");
        assertThat(texts(byDefault.path("postings"), "postingDate")).containsExactly("2026-09-21");
        assertThat(byDefault.path("openCount").asInt()).isEqualTo(3);
        assertThat(company(acmeId, "today")).isEqualTo(byDefault);
        assertThat(texts(company(acmeId, "all").path("postings"), "requisitionId"))
                .containsExactly("R1", "R2", "R3");
    }

    @Test
    void aScopeOtherThanTodayOrAllIsRejected() {
        uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();

        ResponseEntity<JsonNode> response =
                api.getForEntity("/api/companies/" + acmeId + "?scope=closed", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("reason").asText())
                .isEqualTo("Unknown scope \"closed\": use today (the default) or all");
    }

    @Test
    void theRosterCarriesEachCompanysTodayCountAndIsOrderedByItDescendingThenByName() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY)));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY.minusDays(1)));
        stubJobs(
                BETA_JOBS,
                0,
                postings(
                        listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Engineer", ENGINEER_PATH, "Regina, SK", POSTED_YESTERDAY),
                        listing("Zebra Keeper", ZEBRA_KEEPER_PATH, "Regina, SK", POSTED_LONG_AGO)));
        stubDetail(BETA_SITE + CLERK_PATH, detail(PINNED_TODAY));
        stubDetail(BETA_SITE + ENGINEER_PATH, detail(PINNED_TODAY));
        stubJobs(
                ZED_JOBS,
                0,
                postings(
                        listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_TODAY)));
        stubDetail(ZED_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));
        stubDetail(ZED_SITE + CLERK_PATH, detail(PINNED_TODAY));

        uploadAndAwaitRun(THREE_COMPANIES);

        JsonNode roster = companies();
        assertThat(texts(roster, "name")).containsExactly("Beta", "Zed", "Acme");
        assertThat(ints(roster, "todayCount")).containsExactly(2, 2, 0);
        assertThat(ints(roster, "openCount")).containsExactly(3, 2, 1);
        assertThat(company(longs(roster, "id").get(0), "today").path("todayCount").asInt())
                .isEqualTo(2);
    }

    @Test
    void aReuploadKeepsEveryCompanysPostingsSoItsResponseListsTheRosterTheSameWay() {
        stubJobs(
                ZED_JOBS,
                0,
                postings(listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY)));
        stubDetail(ZED_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));
        uploadAndAwaitRun(THREE_COMPANIES);

        JsonNode replaced = uploadRoster(THREE_COMPANIES).getBody().path("companies");

        assertThat(texts(replaced, "name")).containsExactly("Zed", "Acme", "Beta");
        assertThat(ints(replaced, "todayCount")).containsExactly(1, 0, 0);
    }

    @Test
    void aPostingListedOnTwoPagesCostsOneDetailRequest() {
        List<String> firstPage = new ArrayList<>();
        for (int n = 1; n < PAGE; n++) {
            firstPage.add(
                    listing(
                            "Engineer " + n,
                            "/job/Regina-SK/Engineer-%d_E%d".formatted(n, n),
                            "Regina, SK",
                            POSTED_LONG_AGO));
        }
        String accountant = listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY);
        firstPage.add(accountant);
        stubJobs(ACME_JOBS, 0, page(PAGE + 1, firstPage));
        stubJobs(ACME_JOBS, PAGE, postings(accountant));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));

        uploadAndAwaitRun(ACME_ONLY);

        JsonNode roster = companies();
        assertThat(ints(roster, "openCount")).containsExactly(PAGE);
        assertThat(ints(roster, "todayCount")).containsExactly(1);
        assertThat(workday.findAll(getRequestedFor(urlMatching(ACME_SITE + "/job/.*"))))
                .hasSize(1);
    }

    @Test
    void aClosedPostingWithTodaysPostingDateIsTodaysOnlyWhenClosedOnesAreAskedFor() {
        String accountant = listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY);
        String clerk = listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_LONG_AGO);
        stubJobs(ACME_JOBS, 0, postings(accountant, clerk));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));
        uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        stubJobs(ACME_JOBS, 0, postings(clerk));

        startAndAwaitRun();

        JsonNode today = company(acmeId, "today");
        assertThat(today.path("postings")).isEmpty();
        assertThat(today.path("todayCount").asInt()).isZero();
        assertThat(ints(companies(), "todayCount")).containsExactly(0);
        JsonNode todayWithClosed = company(acmeId, "today", true).path("postings");
        assertThat(texts(todayWithClosed, "requisitionId")).containsExactly("R1");
        assertThat(texts(todayWithClosed, "state")).containsExactly("CLOSED");
        assertThat(texts(todayWithClosed, "postingDate")).containsExactly("2026-09-21");
        JsonNode allWithClosed = company(acmeId, "all", true).path("postings");
        assertThat(texts(allWithClosed, "requisitionId")).containsExactly("R1", "R2");
        assertThat(texts(allWithClosed, "state")).containsExactly("CLOSED", "OPEN");
    }

    @Test
    void aDetailThatCannotBeReadStoresThePostingWithoutAPostingDateAndTheCompanySucceeds() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(
                        listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Clerk", CLERK_PATH, "Regina, SK", POSTED_YESTERDAY),
                        listing("Engineer", ENGINEER_PATH, "Regina, SK", POSTED_TODAY),
                        listing("Zebra Keeper", ZEBRA_KEEPER_PATH, "Regina, SK", POSTED_LONG_AGO)));
        workday.stubFor(get(urlEqualTo(ACME_SITE + ACCOUNTANT_PATH)).willReturn(serverError()));
        stubDetail(ACME_SITE + CLERK_PATH, detail(PINNED_TODAY.minusDays(1)));
        workday.stubFor(get(urlEqualTo(ACME_SITE + ENGINEER_PATH)).willReturn(notFound()));

        long runId = uploadAndAwaitRun(ACME_ONLY);

        JsonNode run = run(runId);
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(texts(run.path("outcomes"), "status")).containsExactly("SUCCEEDED");
        assertThat(ints(run.path("outcomes"), "postingsSeen")).containsExactly(4);
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("SUCCEEDED");
        assertThat(roster.get(0).path("errorMessage").isNull()).isTrue();
        assertThat(ints(roster, "openCount")).containsExactly(4);
        assertThat(ints(roster, "todayCount")).containsExactly(0);
        long acmeId = roster.get(0).path("id").asLong();
        assertThat(company(acmeId, "today").path("postings")).isEmpty();
        JsonNode postings = company(acmeId, "all").path("postings");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2", "R3", "R4");
        assertThat(texts(postings, "postingDate")).containsExactly(null, "2026-09-20", null, null);
        assertThat(texts(postings, "postedOnLabel"))
                .containsExactly(POSTED_TODAY, POSTED_YESTERDAY, POSTED_TODAY, POSTED_LONG_AGO);
        assertThat(workday.findAll(getRequestedFor(urlEqualTo(ACME_SITE + ACCOUNTANT_PATH))))
                .hasSize(ATTEMPTS);
        assertThat(workday.findAll(getRequestedFor(urlEqualTo(ACME_SITE + ENGINEER_PATH))))
                .hasSize(1);
    }

    @Test
    void aDetailThatCannotBeReadKeepsThePostingDateAnEarlierRunStored() {
        stubJobs(
                ACME_JOBS,
                0,
                postings(listing("Accountant", ACCOUNTANT_PATH, "Regina, SK", POSTED_TODAY)));
        stubDetail(ACME_SITE + ACCOUNTANT_PATH, detail(PINNED_TODAY));
        uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        workday.stubFor(get(urlEqualTo(ACME_SITE + ACCOUNTANT_PATH)).willReturn(serverError()));

        long runId = startAndAwaitRun();

        assertThat(run(runId).path("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode today = company(acmeId, "today");
        assertThat(today.path("todayCount").asInt()).isEqualTo(1);
        assertThat(texts(today.path("postings"), "requisitionId")).containsExactly("R1");
        assertThat(texts(today.path("postings"), "postingDate")).containsExactly("2026-09-21");
        assertThat(ints(companies(), "todayCount")).containsExactly(1);
        assertThat(workday.findAll(getRequestedFor(urlEqualTo(ACME_SITE + ACCOUNTANT_PATH))))
                .hasSize(1 + ATTEMPTS);
    }
}
