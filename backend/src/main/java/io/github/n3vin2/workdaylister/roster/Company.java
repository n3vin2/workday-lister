package io.github.n3vin2.workdaylister.roster;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * An employer the user is tracking: a display name from the CSV and exactly one Career Site. The
 * Career Site coordinates are unique across the Roster (see {@code V2__company.sql}); the id is
 * stable across Roster uploads that keep the same Career Site, so anything that later hangs off a
 * Company survives a re-upload.
 *
 * <p>The status, last scraped time, Open posting count, truncated flag and error message are what
 * the latest Scrape Run left behind, denormalised here so the Roster screen needs no join
 * ({@code V3__scrape_run_and_job_posting.sql}, {@code V6__error_message.sql}). A Roster upload and
 * a Scrape Run may touch the same
 * row at the same time, the upload its name and URL and the run its results, so updates write only
 * the columns that changed rather than the whole row.
 */
@Entity
@Table(name = "company")
@DynamicUpdate
public class Company {

    /**
     * Width of the {@code error_message} columns ({@code V6__error_message.sql}); a longer reason
     * is cut to fit before it is recorded.
     */
    public static final int ERROR_MESSAGE_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** The URL as supplied in the CSV, before parsing. */
    @Column(name = "career_site_url", nullable = false, length = 2048)
    private String careerSiteUrl;

    @Embedded
    private CareerSite careerSite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyStatus status;

    @Column(name = "last_scraped_at")
    private Instant lastScrapedAt;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    /** Whether the Career Site reported Workday's 2,000 posting cap, so the count is a floor. */
    @Column(nullable = false)
    private boolean truncated;

    /** Why the latest Scrape Run could not read the Career Site; null unless FAILED. */
    @Column(name = "error_message", length = ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    protected Company() {}

    /** A Company that has never been scraped, as first described by a Roster CSV row. */
    public Company(RosterEntry entry) {
        this.name = entry.name();
        this.careerSiteUrl = entry.careerSiteUrl();
        this.careerSite = entry.careerSite();
        this.status = CompanyStatus.NEVER_SCRAPED;
    }

    /** Adopts the name and URL from a newer CSV row for the same Career Site. */
    void adopt(RosterEntry entry) {
        this.name = entry.name();
        this.careerSiteUrl = entry.careerSiteUrl();
    }

    /**
     * A Scrape Run has started reading this Company's Career Site; whatever went wrong last time
     * no longer describes it.
     */
    public void beginScrape() {
        this.status = CompanyStatus.IN_PROGRESS;
        this.errorMessage = null;
    }

    /** A Scrape Run has finished with this Company: its Open postings are counted and stored. */
    public void finishScrape(Instant at, int openCount, boolean truncated) {
        this.status = CompanyStatus.SUCCEEDED;
        this.lastScrapedAt = at;
        this.openCount = openCount;
        this.truncated = truncated;
    }

    /**
     * A Scrape Run could not read this Company's Career Site, for the given reason: nothing was
     * applied, so the postings, count and last scraped time are still the previous run's.
     */
    public void failScrape(String reason) {
        this.status = CompanyStatus.FAILED;
        this.errorMessage = reason;
    }

    /**
     * A Scrape Run was cancelled while reading this Company's Career Site: nothing it listed was
     * applied, so the postings, count and last scraped time are still the previous run's.
     */
    public void cancelScrape() {
        this.status = CompanyStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCareerSiteUrl() {
        return careerSiteUrl;
    }

    public CareerSite getCareerSite() {
        return careerSite;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public Instant getLastScrapedAt() {
        return lastScrapedAt;
    }

    public int getOpenCount() {
        return openCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
