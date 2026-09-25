package io.github.n3vin2.workdaylister.workday;

import io.github.n3vin2.workdaylister.roster.CareerSite;

/**
 * Reads a Career Site through Workday's undocumented CXS JSON endpoint (ADR-0001). This is the only
 * module that should need to change if Workday changes the contract.
 *
 * <p>Two operations: list one page of Job Postings, and fetch one posting's detail. Paging is the
 * caller's: the endpoint answers in pages of {@link #PAGE_SIZE}, reports the {@code total} on the
 * first page only, and never lists a posting past {@link #MAX_POSTINGS}.
 *
 * <p>Pacing and retry are the client's: requests are separated by the configured pause, and a
 * throttled or failed request is retried with backoff. Either operation throws
 * {@link RequestFailedException} once the request has failed for good.
 */
public interface WorkdayClient {

    /**
     * A request to a Career Site failed after any retries: Workday answered with an error status
     * (a throttled or failed request, after the last retry), the Career Site could not be reached,
     * or the response was not what the endpoint sends. The message is the short reason the Roster
     * screen shows next to a failed Company.
     */
    final class RequestFailedException extends RuntimeException {
        RequestFailedException(String reason) {
            super(reason);
        }
    }

    /** Postings per page. Workday answers in twenties; the request asks for exactly this many. */
    int PAGE_SIZE = 20;

    /**
     * The endpoint's cap: it never lists postings past this offset, and a Career Site with more
     * postings reports exactly this many as its total. Such a Company is truncated.
     */
    int MAX_POSTINGS = 2000;

    /**
     * One page of a Career Site's postings, starting at {@code offset}, a multiple of
     * {@link #PAGE_SIZE}.
     *
     * @throws RequestFailedException when the page could not be read
     */
    JobPage listJobs(CareerSite site, int offset);

    /**
     * One posting's detail, by the external path its listing carried.
     *
     * @throws RequestFailedException when the detail could not be read
     */
    JobDetail fetchDetail(CareerSite site, String externalPath);
}
