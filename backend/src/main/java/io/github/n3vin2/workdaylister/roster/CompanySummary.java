package io.github.n3vin2.workdaylister.roster;

import java.time.Instant;
import java.util.List;

/**
 * One Company as the Roster screen lists it: name, status, how many postings are Open, when it was
 * last scraped ({@code null} until it has been), and whether that count is a floor because the
 * Career Site hit Workday's cap.
 */
public record CompanySummary(
        long id,
        String name,
        CompanyStatus status,
        int openCount,
        Instant lastScrapedAt,
        boolean truncated) {

    static CompanySummary of(Company company) {
        return new CompanySummary(
                company.getId(),
                company.getName(),
                company.getStatus(),
                company.getOpenCount(),
                company.getLastScrapedAt(),
                company.isTruncated());
    }

    static List<CompanySummary> ofAll(List<Company> companies) {
        return companies.stream().map(CompanySummary::of).toList();
    }
}
