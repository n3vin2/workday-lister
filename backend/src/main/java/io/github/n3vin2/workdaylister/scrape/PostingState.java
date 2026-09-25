package io.github.n3vin2.workdaylister.scrape;

/**
 * Whether a Job Posting is Open, its Career Site still listing it, or Closed, a Scrape Run having
 * found it gone. Closed postings are kept, not deleted, and become Open again if listed again.
 */
public enum PostingState {
    OPEN,
    CLOSED
}
