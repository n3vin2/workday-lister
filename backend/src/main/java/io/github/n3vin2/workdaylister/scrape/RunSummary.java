package io.github.n3vin2.workdaylister.scrape;

import java.time.Instant;
import java.util.List;

/**
 * One Scrape Run as the API reports it: how far it has got (Companies done out of the total) and
 * what it did for each Company. The Roster screen polls the active run's summary for live
 * progress; finished runs are stored and readable, not displayed, as there is no run history
 * screen yet.
 */
public record RunSummary(
        long id,
        Instant startedAt,
        Instant finishedAt,
        ScrapeRunStatus status,
        int done,
        int total,
        List<OutcomeSummary> outcomes) {

    /** One Company's outcome within a run, with why it failed ({@code null} unless it did). */
    public record OutcomeSummary(
            long companyId,
            String name,
            OutcomeStatus status,
            Instant startedAt,
            Instant finishedAt,
            int postingsSeen,
            boolean truncated,
            String errorMessage) {

        static OutcomeSummary of(CompanyOutcome outcome) {
            return new OutcomeSummary(
                    outcome.getCompany().getId(),
                    outcome.getCompany().getName(),
                    outcome.getStatus(),
                    outcome.getStartedAt(),
                    outcome.getFinishedAt(),
                    outcome.getPostingsSeen(),
                    outcome.isTruncated(),
                    outcome.getErrorMessage());
        }
    }

    /** A run with its outcomes, in the order the run visits its Companies. */
    public static RunSummary of(ScrapeRun run, List<CompanyOutcome> outcomes) {
        int done = (int) outcomes.stream().filter(outcome -> outcome.getStatus().isDone()).count();
        return new RunSummary(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                done,
                outcomes.size(),
                outcomes.stream().map(OutcomeSummary::of).toList());
    }
}
