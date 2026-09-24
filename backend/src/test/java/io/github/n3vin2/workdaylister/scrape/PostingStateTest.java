package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import org.junit.jupiter.api.Test;

/**
 * Open and Closed postings: what a Scrape Run does to a Company's stored postings once it has read
 * everything its Career Site lists, and how {@code GET /api/companies/{id}} (with and without
 * {@code includeClosed=true}) and {@code GET /api/companies} report them.
 */
class PostingStateTest extends IntegrationHarness {

    private static final String ACME_JOBS = "/wday/cxs/acme/Careers/jobs";
    private static final String ACME_ONLY =
            """
            company,url
            Acme,https://acme.wd1.myworkdayjobs.com/Careers
            """;

    private static final String ACCOUNTANT =
            listing("Accountant", "/job/Saskatoon-SK/Accountant_R1", "Saskatoon, SK");
    private static final String ZEBRA_KEEPER =
            listing("Zebra Keeper", "/job/Regina-SK/Zebra-Keeper_R2", "Regina, SK");
    private static final String CLERK = listing("Clerk", "/job/Regina-SK/Clerk_R3", "Regina, SK");

    @Test
    void aRunClosesThePostingsTheCareerSiteNoLongerListsAndCountsOnlyOpenOnes() {
        stubJobs(ACME_JOBS, 0, postings(ACCOUNTANT, ZEBRA_KEEPER));
        uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        stubJobs(ACME_JOBS, 0, postings(ZEBRA_KEEPER, CLERK));

        startAndAwaitRun();

        JsonNode company = company(acmeId);
        assertThat(texts(company.path("postings"), "requisitionId")).containsExactly("R3", "R2");
        assertThat(texts(company.path("postings"), "state")).containsOnly("OPEN");
        assertThat(company.path("openCount").asInt()).isEqualTo(2);
        assertThat(ints(companies(), "openCount")).containsExactly(2);
    }

    @Test
    void includeClosedRevealsAClosedPostingWithEverythingItHadWhenLastSeen() {
        stubJobs(ACME_JOBS, 0, postings(ACCOUNTANT, ZEBRA_KEEPER));
        long firstRun = uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        stubJobs(ACME_JOBS, 0, postings(ZEBRA_KEEPER));
        long secondRun = startAndAwaitRun();

        JsonNode postings = company(acmeId, true).path("postings");

        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2");
        assertThat(texts(postings, "state")).containsExactly("CLOSED", "OPEN");
        assertThat(longs(postings, "firstSeenRunId")).containsOnly(firstRun);
        assertThat(longs(postings, "lastSeenRunId")).containsExactly(firstRun, secondRun);
        JsonNode accountant = postings.get(0);
        assertThat(accountant.path("title").asText()).isEqualTo("Accountant");
        assertThat(accountant.path("locationText").asText()).isEqualTo("Saskatoon, SK");
        assertThat(accountant.path("postedOnLabel").asText()).isEqualTo("Posted Today");
        assertThat(accountant.path("publicUrl").asText())
                .isEqualTo("https://acme.wd1.myworkdayjobs.com/Careers/job/Saskatoon-SK/Accountant_R1");
    }

    @Test
    void aClosedPostingTheCareerSiteListsAgainIsReopenedKeepingItsFirstSeen() {
        stubJobs(ACME_JOBS, 0, postings(ACCOUNTANT, ZEBRA_KEEPER));
        long firstRun = uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        stubJobs(ACME_JOBS, 0, postings(ZEBRA_KEEPER));
        startAndAwaitRun();
        stubJobs(ACME_JOBS, 0, postings(ACCOUNTANT, ZEBRA_KEEPER));

        long thirdRun = startAndAwaitRun();

        JsonNode company = company(acmeId);
        JsonNode postings = company.path("postings");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2");
        assertThat(texts(postings, "state")).containsOnly("OPEN");
        assertThat(longs(postings, "firstSeenRunId")).containsOnly(firstRun);
        assertThat(longs(postings, "lastSeenRunId")).containsOnly(thirdRun);
        assertThat(company.path("openCount").asInt()).isEqualTo(2);
    }

    @Test
    void aRunThatCannotReadTheCareerSiteChangesNoPostingStates() {
        stubJobs(ACME_JOBS, 0, postings(ACCOUNTANT, ZEBRA_KEEPER));
        long firstRun = uploadAndAwaitRun(ACME_ONLY);
        long acmeId = companies().get(0).path("id").asLong();
        workday.stubFor(post(urlEqualTo(ACME_JOBS)).willReturn(serverError()));

        startAndAwaitRun();

        JsonNode company = company(acmeId);
        JsonNode postings = company.path("postings");
        assertThat(texts(postings, "requisitionId")).containsExactly("R1", "R2");
        assertThat(texts(postings, "state")).containsOnly("OPEN");
        assertThat(longs(postings, "lastSeenRunId")).containsOnly(firstRun);
        assertThat(company.path("openCount").asInt()).isEqualTo(2);
        assertThat(company(acmeId, true).path("postings")).hasSize(2);
    }
}
