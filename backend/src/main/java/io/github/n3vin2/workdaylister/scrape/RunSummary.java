package io.github.n3vin2.workdaylister.scrape;

import java.time.Instant;
import java.util.List;

/**
 * One Scrape Run as the API reports it, with what it did for each Company. Runs are stored and
 * readable, not displayed: there is no run history screen yet.
 */
public record RunSummary(
        long id,
        Instant startedAt,
        Instant finishedAt,
        ScrapeRunStatus status,
        List<OutcomeSummary> companies) {

    /** One Company's outcome within a run. */
    public record OutcomeSummary(
            long companyId,
            String name,
            OutcomeStatus status,
            Instant startedAt,
            Instant finishedAt,
            int postingsSeen,
            boolean truncated) {

        static OutcomeSummary of(CompanyOutcome outcome) {
            return new OutcomeSummary(
                    outcome.getCompany().getId(),
                    outcome.getCompany().getName(),
                    outcome.getStatus(),
                    outcome.getStartedAt(),
                    outcome.getFinishedAt(),
                    outcome.getPostingsSeen(),
                    outcome.isTruncated());
        }
    }

    public static RunSummary of(ScrapeRun run, List<CompanyOutcome> outcomes) {
        return new RunSummary(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                outcomes.stream().map(OutcomeSummary::of).toList());
    }
}
