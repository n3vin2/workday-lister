package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.github.n3vin2.workdaylister.PinnedClockConfig.PINNED_TODAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_LONG_AGO;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_TODAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.POSTED_YESTERDAY;
import static io.github.n3vin2.workdaylister.WorkdayPages.detail;
import static io.github.n3vin2.workdaylister.WorkdayPages.listing;
import static io.github.n3vin2.workdaylister.WorkdayPages.postings;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Today's Postings (ADR-0002): which postings a Scrape Run fetches a detail for, the Posting Date
 * it stores, and how {@code GET /api/companies} and {@code GET /api/companies/{id}?scope=...}
 * report today's, against a stubbed Workday and a clock pinned to
 * {@link io.github.n3vin2.workdaylister.PinnedClockConfig#PINNED_TODAY}.
 */
class TodaysPostingsTest extends IntegrationHarness {

    private static final String ACME_SITE = "/wday/cxs/acme/Careers";
    private static final String ACME_JOBS = ACME_SITE + "/jobs";
    private static final String BETA_SITE = "/wday/cxs/beta/Jobs";
    private static final String BETA_JOBS = BETA_SITE + "/jobs";
    private static final String ZED_SITE = "/wday/cxs/zed/Careers";
    private static final String ZED_JOBS = ZED_SITE + "/jobs";

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
        // A re-upload keeps every Company's postings, so its response lists the Roster the same
        // way.
        JsonNode replaced = uploadRoster(THREE_COMPANIES).getBody().path("companies");
        assertThat(texts(replaced, "name")).containsExactly("Beta", "Zed", "Acme");
        assertThat(ints(replaced, "todayCount")).containsExactly(2, 2, 0);
    }

    /** Uploads the Roster, which starts a run, waits for that run to finish, and returns its id. */
    private long uploadAndAwaitRun(String csv) {
        ResponseEntity<JsonNode> upload = uploadRoster(csv);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        long runId = upload.getBody().path("run").path("id").asLong();
        awaitRunFinished(runId);
        return runId;
    }
}
