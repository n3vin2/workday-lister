package io.github.n3vin2.workdaylister.scrape;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One pass over the whole Roster, fetching every Job Posting from every Career Site one Company at
 * a time. Its per-Company results are {@link CompanyOutcome} rows; it is the run that postings
 * record as First Seen and Last Seen.
 */
@Entity
@Table(name = "scrape_run")
public class ScrapeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScrapeRunStatus status;

    protected ScrapeRun() {}

    /** A run starting now. */
    ScrapeRun(Instant startedAt) {
        this.startedAt = startedAt;
        this.status = ScrapeRunStatus.RUNNING;
    }

    /**
     * Every Company has been visited: the run succeeded, or partially failed if any Company's
     * Career Site could not be read.
     */
    void finish(Instant at, boolean anyCompanyFailed) {
        this.finishedAt = at;
        this.status =
                anyCompanyFailed ? ScrapeRunStatus.PARTIALLY_FAILED : ScrapeRunStatus.SUCCEEDED;
    }

    /** Stopped at the user's request before every Company was visited. */
    void cancel(Instant at) {
        this.finishedAt = at;
        this.status = ScrapeRunStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public ScrapeRunStatus getStatus() {
        return status;
    }
}
