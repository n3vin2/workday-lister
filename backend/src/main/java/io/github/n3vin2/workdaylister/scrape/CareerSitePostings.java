package io.github.n3vin2.workdaylister.scrape;

import java.util.List;

/**
 * Everything a Career Site listed in one pass, each with its Posting Date where one was fetched,
 * and whether Workday's cap cut the list short so the Company is truncated.
 */
record CareerSitePostings(List<ScrapedPosting> postings, boolean truncated) {}
