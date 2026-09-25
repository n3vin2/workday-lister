package io.github.n3vin2.workdaylister.scrape;

/**
 * Where a Scrape Run is: still walking the Roster, done with every Company, done but with at least
 * one Company whose Career Site could not be read, or stopped early at the user's request.
 */
public enum ScrapeRunStatus {
    RUNNING,
    SUCCEEDED,
    PARTIALLY_FAILED,
    CANCELLED
}
