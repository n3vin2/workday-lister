package io.github.n3vin2.workdaylister.scrape;

/**
 * Where a Scrape Run is: still walking the Roster, done with every Company, or stopped early at
 * the user's request. Further states arrive with later tickets.
 */
public enum ScrapeRunStatus {
    RUNNING,
    SUCCEEDED,
    CANCELLED
}
