package io.github.n3vin2.workdaylister.roster;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One Company as the Roster screen lists it: name, status, how many of Today's Postings it has,
 * how many postings are Open, when it was last scraped ({@code null} until it has been), whether
 * that count is a floor because the Career Site hit Workday's cap, and why the latest run failed
 * ({@code null} unless it did).
 */
public record CompanySummary(
        long id,
        String name,
        CompanyStatus status,
        int todayCount,
        int openCount,
        Instant lastScrapedAt,
        boolean truncated,
        String errorMessage) {

    /**
     * The Roster screen's order: the Companies with the most Today's Postings first, then by name.
     */
    private static final Comparator<CompanySummary> ROSTER_ORDER =
            Comparator.comparingInt(CompanySummary::todayCount)
                    .reversed()
                    .thenComparing(CompanySummary::name, String.CASE_INSENSITIVE_ORDER);

    static CompanySummary of(Company company, int todayCount) {
        return new CompanySummary(
                company.getId(),
                company.getName(),
                company.getStatus(),
                todayCount,
                company.getOpenCount(),
                company.getLastScrapedAt(),
                company.isTruncated(),
                company.getErrorMessage());
    }

    /**
     * The given Companies as the Roster screen lists them, by today's count descending and then by
     * name. {@code todayCounts} is keyed by Company id; a Company absent from it has none.
     */
    static List<CompanySummary> ofAll(List<Company> companies, Map<Long, Integer> todayCounts) {
        return companies.stream()
                .map(company -> of(company, todayCounts.getOrDefault(company.getId(), 0)))
                .sorted(ROSTER_ORDER)
                .toList();
    }
}
