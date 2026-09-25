package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import io.github.n3vin2.workdaylister.roster.Company;
import io.github.n3vin2.workdaylister.roster.CompanyRepository;
import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
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
 * Every step reloads what it touches by id; nothing detached is saved back. The Roster cannot
 * change under a run, because an upload is refused while one is active, so what a step reloads is
 * always there.
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
        return open(companies.findAllByOrderByNameAsc());
    }

    /**
     * Opens a run over just one Company, the per-Company Retry, with a QUEUED outcome for it.
     * Empty when no Company has that id.
     */
    @Transactional
    public Optional<ScrapeRun> openFor(long companyId) {
        return open(companies.findById(companyId).stream().toList());
    }

    private Optional<ScrapeRun> open(List<Company> toVisit) {
        if (toVisit.isEmpty()) {
            return Optional.empty();
        }
        ScrapeRun run = runs.save(new ScrapeRun(Instant.now(clock)));
        outcomes.saveAll(
                toVisit.stream().map(company -> new CompanyOutcome(run, company)).toList());
        return Optional.of(run);
    }

    /** The ids of a run's outcomes in visiting order. */
    @Transactional(readOnly = true)
    public List<Long> outcomeIds(long runId) {
        return outcomes.findAllByRunIdOrderByCompanyNameAsc(runId).stream()
                .map(CompanyOutcome::getId)
                .toList();
    }

    /** Marks an outcome and its Company in progress and returns the Career Site to read. */
    @Transactional
    public CareerSite begin(long outcomeId) {
        CompanyOutcome outcome = outcomes.findById(outcomeId).orElseThrow();
        outcome.begin(Instant.now(clock));
        outcome.getCompany().beginScrape();
        return outcome.getCompany().getCareerSite();
    }

    /**
     * Stores what a Career Site listed: unseen postings are inserted with First Seen set to this
     * run, and every listed posting has Last Seen set to it. A posting Workday lists twice across
     * pages (its paging shifts as postings appear) counts once. Then the Company and its outcome
     * are marked succeeded: the outcome with the postings this run saw, the Company with its Open
     * count, which is every posting stored for it until Closed handling arrives with a later ticket.
     */
    @Transactional
    public void record(long outcomeId, CareerSitePostings listed) {
        CompanyOutcome outcome = outcomes.findById(outcomeId).orElseThrow();
        Company company = outcome.getCompany();
        ScrapeRun run = outcome.getRun();
        Map<String, JobPosting> known = new HashMap<>();
        for (JobPosting posting :
                postings.findAllByCompanyOrderByTitleAscRequisitionIdAsc(company)) {
            known.put(posting.getRequisitionId(), posting);
        }
        Set<String> seen = new HashSet<>();
        for (WorkdayPosting listing : listed.postings()) {
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
        int openCount = (int) postings.countByCompany(company);
        company.finishScrape(now, openCount, listed.truncated());
        outcome.succeed(now, seen.size(), listed.truncated());
    }

    /**
     * The run was cancelled while reading this Company's Career Site: the outcome and the Company
     * are marked cancelled and nothing the Career Site listed is applied, so the Company keeps the
     * previous run's postings and count.
     */
    @Transactional
    public void cancelCompany(long outcomeId) {
        CompanyOutcome outcome = outcomes.findById(outcomeId).orElseThrow();
        outcome.cancel(Instant.now(clock));
        outcome.getCompany().cancelScrape();
    }

    /**
     * The Company's Career Site could not be read, for the given reason: the outcome and the
     * Company are marked failed with it, and nothing is applied, so the Company keeps the previous
     * run's postings, count and last scraped time.
     */
    @Transactional
    public void fail(long outcomeId, String reason) {
        CompanyOutcome outcome = outcomes.findById(outcomeId).orElseThrow();
        outcome.fail(Instant.now(clock), reason);
        outcome.getCompany().failScrape(reason);
    }

    /** Every Company has been visited; the run partially failed if any of them failed. */
    @Transactional
    public void close(long runId) {
        boolean anyCompanyFailed =
                outcomes.existsByRunIdAndStatus(runId, OutcomeStatus.FAILED);
        runs.findById(runId).orElseThrow().finish(Instant.now(clock), anyCompanyFailed);
    }

    /**
     * The run stopped at the user's request: the Companies it had not reached are marked cancelled
     * (their own status is untouched, as nothing happened to them) and so is the run. Companies it
     * finished keep their Job Postings and Company Outcome.
     */
    @Transactional
    public void cancel(long runId) {
        Instant now = Instant.now(clock);
        outcomes.findAllByRunIdAndStatus(runId, OutcomeStatus.QUEUED)
                .forEach(outcome -> outcome.cancel(now));
        runs.findById(runId).orElseThrow().cancel(now);
    }
}
