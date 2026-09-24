package io.github.n3vin2.workdaylister.roster;

/**
 * What the most recent Scrape Run did with a Company: nothing yet, currently reading its Career
 * Site, or finished. Failure and cancellation states arrive with later tickets.
 */
public enum CompanyStatus {
    NEVER_SCRAPED,
    IN_PROGRESS,
    SUCCEEDED
}
