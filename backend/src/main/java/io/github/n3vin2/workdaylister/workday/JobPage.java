package io.github.n3vin2.workdaylister.workday;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One page of Workday's jobs endpoint.
 *
 * @param total how many postings the Career Site has, capped at {@link WorkdayClient#MAX_POSTINGS};
 *     Workday reports it on the first page only and sends 0 on later pages
 * @param postings the postings on this page, up to {@link WorkdayClient#PAGE_SIZE}
 */
public record JobPage(int total, @JsonProperty("jobPostings") List<JobListing> postings) {}
