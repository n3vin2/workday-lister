package io.github.n3vin2.workdaylister.roster;

/**
 * What the most recent Scrape Run did with a Company: nothing yet, currently reading its Career
 * Site, finished, failed to read it (the Company then carries the reason), or cancelled part-way
 * through reading it.
 */
public enum CompanyStatus {
    NEVER_SCRAPED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
