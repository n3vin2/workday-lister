package io.github.n3vin2.workdaylister.scrape;

/**
 * Where a Scrape Run is: still walking the Roster, or done. Further states arrive with later
 * tickets.
 */
public enum ScrapeRunStatus {
    RUNNING,
    SUCCEEDED
}
