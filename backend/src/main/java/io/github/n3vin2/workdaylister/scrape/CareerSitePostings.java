package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
import java.util.List;

/**
 * Everything a Career Site listed in one pass, and whether Workday's cap cut the list short so the
 * Company is truncated.
 */
record CareerSitePostings(List<WorkdayPosting> postings, boolean truncated) {}
