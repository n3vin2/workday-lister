package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * What one Scrape Run did with one Company: when it started and finished reading the Career Site,
 * how many postings it saw, and whether Workday's cap truncated the list. One row per (run,
 * Company), created QUEUED when the run starts.
 */
@Entity
@Table(name = "company_outcome")
public class CompanyOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scrape_run_id", nullable = false)
    private ScrapeRun run;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutcomeStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "postings_seen", nullable = false)
    private int postingsSeen;

    @Column(nullable = false)
    private boolean truncated;

    protected CompanyOutcome() {}

    /** A Company waiting its turn in a run. */
    CompanyOutcome(ScrapeRun run, Company company) {
        this.run = run;
        this.company = company;
        this.status = OutcomeStatus.QUEUED;
    }

    /** The run has started reading this Company's Career Site. */
    void begin(Instant at) {
        this.status = OutcomeStatus.IN_PROGRESS;
        this.startedAt = at;
    }

    /** The run has stored everything the Career Site listed. */
    void succeed(Instant at, int postingsSeen, boolean truncated) {
        this.status = OutcomeStatus.SUCCEEDED;
        this.finishedAt = at;
        this.postingsSeen = postingsSeen;
        this.truncated = truncated;
    }

    public Long getId() {
        return id;
    }

    public ScrapeRun getRun() {
        return run;
    }

    public Company getCompany() {
        return company;
    }

    public OutcomeStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getPostingsSeen() {
        return postingsSeen;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
