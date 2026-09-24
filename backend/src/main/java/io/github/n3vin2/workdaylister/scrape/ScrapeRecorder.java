package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import io.github.n3vin2.workdaylister.roster.Company;
import io.github.n3vin2.workdaylister.roster.CompanyRepository;
import io.github.n3vin2.workdaylister.workday.JobListing;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a Scrape Run's progress to the database, one transaction per step, so a run in progress
 * is visible to readers between steps and no transaction is held open while Workday is being read.
 * Every step reloads what it touches by id: a Company that leaves the Roster mid-run (a new upload)
 * takes its outcome with it, and the step then does nothing rather than resurrect it.
 */
@Service
class ScrapeRecorder {

    private final ScrapeRunRepository runs;
    private final CompanyOutcomeRepository outcomes;
    private final CompanyRepository companies;
    private final JobPostingRepository postings;
    private final Clock clock;

    ScrapeRecorder(
            ScrapeRunRepository runs,
            CompanyOutcomeRepository outcomes,
            CompanyRepository companies,
            JobPostingRepository postings,
            Clock clock) {
        this.runs = runs;
        this.outcomes = outcomes;
        this.companies = companies;
        this.postings = postings;
        this.clock = clock;
    }

    /**
     * Opens a run over the current Roster, with a QUEUED outcome for every Company. Empty when the
     * Roster is empty: there is nothing to run over.
     */
    @Transactional
    public Optional<ScrapeRun> open() {
        List<Company> roster = companies.findAllByOrderByNameAsc();
        if (roster.isEmpty()) {
            return Optional.empty();
        }
        ScrapeRun run = runs.save(new ScrapeRun(Instant.now(clock)));
        outcomes.saveAll(roster.stream().map(company -> new CompanyOutcome(run, company)).toList());
        return Optional.of(run);
    }

    /** The ids of a run's outcomes in visiting order. */
    @Transactional(readOnly = true)
    public List<Long> outcomeIds(long runId) {
        return outcomes.findAllByRunIdOrderByCompanyNameAsc(runId).stream()
                .map(CompanyOutcome::getId)
                .toList();
    }

    /**
     * Marks an outcome and its Company in progress and returns the Career Site to read; empty when
     * the Company has left the Roster since the run was opened.
     */
    @Transactional
    public Optional<CareerSite> begin(long outcomeId) {
        return outcomes.findById(outcomeId)
                .map(
                        outcome -> {
                            outcome.begin(Instant.now(clock));
                            outcome.getCompany().beginScrape();
                            return outcome.getCompany().getCareerSite();
                        });
    }

    /**
     * Stores what a Career Site listed: unseen postings are inserted with First Seen set to this
     * run, and every listed posting has Last Seen set to it. A posting Workday lists twice across
     * pages (its paging shifts as postings appear) counts once. Then the Company and its outcome are
     * marked succeeded: the outcome with the postings this run saw, the Company with its Open count,
     * which is every posting stored for it until Closed handling arrives with a later ticket.
     */
    @Transactional
    public void record(long outcomeId, List<JobListing> listings, boolean truncated) {
        outcomes.findById(outcomeId)
                .ifPresent(
                        outcome -> {
                            Company company = outcome.getCompany();
                            ScrapeRun run = outcome.getRun();
                            Map<String, JobPosting> known = new HashMap<>();
                            for (JobPosting posting :
                                    postings.findAllByCompanyOrderByTitleAscRequisitionIdAsc(
                                            company)) {
                                known.put(posting.getRequisitionId(), posting);
                            }
                            Set<String> seen = new HashSet<>();
                            for (JobListing listing : listings) {
                                if (!seen.add(listing.requisitionId())) {
                                    continue;
                                }
                                JobPosting posting = known.get(listing.requisitionId());
                                if (posting == null) {
                                    postings.save(new JobPosting(company, listing, run));
                                } else {
                                    posting.seen(listing, run);
                                }
                            }
                            Instant now = Instant.now(clock);
                            company.finishScrape(
                                    now, (int) postings.countByCompany(company), truncated);
                            outcome.succeed(now, seen.size(), truncated);
                        });
    }

    /** Every Company has been visited. */
    @Transactional
    public void close(long runId) {
        runs.findById(runId).ifPresent(run -> run.finish(Instant.now(clock)));
    }
}
