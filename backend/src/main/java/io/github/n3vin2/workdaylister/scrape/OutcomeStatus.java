package io.github.n3vin2.workdaylister.scrape;

/**
 * Where one Company is within a Scrape Run: waiting its turn, having its Career Site read, or done.
 * Further states arrive with later tickets.
 */
public enum OutcomeStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED
}
