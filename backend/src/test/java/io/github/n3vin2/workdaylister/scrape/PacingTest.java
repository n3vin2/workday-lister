package io.github.n3vin2.workdaylister.scrape;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Request pacing and retry backoff, observed through the stub's request log in a context of their
 * own where both are long enough to measure (ADR-0001: Workday rate-limits by source IP).
 * Consecutive requests are at least the pacing interval apart, and the retries of a failed request
 * wait exponentially longer. Every assertion is a lower bound on the time between two requests,
 * which the client's wait guarantees, so no test races a delay against a deadline.
 */
@TestPropertySource(properties = {"scraper.pacing-interval=200ms", "scraper.retry-backoff=300ms"})
class PacingTest extends IntegrationHarness {

    private static final String ACME_JOBS = "/wday/cxs/acme/Careers/jobs";
    private static final String BETA_JOBS = "/wday/cxs/beta/Jobs/jobs";
    private static final String ANY_JOBS = "/wday/cxs/.*/jobs";

    private static final Duration PACING = Duration.ofMillis(200);
    private static final Duration BACKOFF = Duration.ofMillis(300);

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

    @Test
    void consecutiveRequestsAreSeparatedByThePacingIntervalAcrossPagesAndCompanies() {
        stubJobs(ACME_JOBS, 0, jobsPage(25, 0, 20));
        stubJobs(ACME_JOBS, 20, jobsPage(0, 20, 5));
        stubJobs(BETA_JOBS, 0, jobsPage(1, 0, 1));

        uploadAndAwaitRun(TWO_COMPANIES);

        List<LoggedRequest> requests = workday.findAll(postRequestedFor(urlMatching(ANY_JOBS)));
        assertThat(requests).hasSize(3);
        assertThat(gaps(requests))
                .allSatisfy(gap -> assertThat(gap).isGreaterThanOrEqualTo(PACING));
        assertThat(ints(companies(), "openCount")).containsExactly(25, 1);
    }

    @Test
    void aFailedRequestIsRetriedWithExponentialBackoffBeforeTheCompanyIsMarkedFailed() {
        workday.stubFor(post(urlEqualTo(ACME_JOBS)).willReturn(serviceUnavailable()));

        uploadAndAwaitRun(ACME_ONLY);

        List<LoggedRequest> requests = workday.findAll(postRequestedFor(urlEqualTo(ACME_JOBS)));
        assertThat(requests).hasSize(4);
        List<Duration> gaps = gaps(requests);
        assertThat(gaps.get(0)).isGreaterThanOrEqualTo(BACKOFF);
        assertThat(gaps.get(1)).isGreaterThanOrEqualTo(BACKOFF.multipliedBy(2));
        assertThat(gaps.get(2)).isGreaterThanOrEqualTo(BACKOFF.multipliedBy(4));
        JsonNode roster = companies();
        assertThat(texts(roster, "status")).containsExactly("FAILED");
        assertThat(roster.get(0).path("errorMessage").asText())
                .isEqualTo("Career Site answered HTTP 503 after 3 retries");
    }
}
