package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
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
import java.time.LocalDate;

/**
 * One position listed on a Career Site, identified within its Company by the requisition ID (see
 * {@code V3__scrape_run_and_job_posting.sql}). A Scrape Run inserts a posting the first time it
 * lists it and refreshes it on every later sighting, so the same posting is recognised across runs
 * even if its title changes. First Seen and Last Seen are the runs that did so.
 *
 * <p>A posting is Open while its Career Site lists it and Closed once a run that read the whole
 * Career Site found it gone ({@code V7__job_posting_state.sql}). Closed postings keep everything
 * they had when last seen, and a later sighting reopens them without touching First Seen.
 *
 * <p>The Posting Date is the calendar date Workday says the posting went live
 * ({@code V8__posting_date.sql}). A run learns it only for postings labelled "Posted Today" or
 * "Posted Yesterday" (ADR-0002) whose detail it could read, and once known it is kept: a later
 * sighting under another label, or whose detail could not be read, says nothing new about when
 * the posting went live.
 */
@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "requisition_id", nullable = false)
    private String requisitionId;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(name = "external_path", nullable = false, length = 1024)
    private String externalPath;

    @Column(name = "location_text", length = 1024)
    private String locationText;

    /** Workday's relative label as last seen, for example {@code Posted Today}; never a date. */
    @Column(name = "posted_on_label")
    private String postedOnLabel;

    /** The Posting Date, a bare date in no timezone; {@code null} until a run has fetched it. */
    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Column(name = "public_url", nullable = false, length = 2048)
    private String publicUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PostingState state;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_seen_run_id", nullable = false)
    private ScrapeRun firstSeenRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_seen_run_id", nullable = false)
    private ScrapeRun lastSeenRun;

    protected JobPosting() {}

    /** A posting listed for the first time, by the given run; Open, like anything just listed. */
    JobPosting(Company company, ScrapedPosting scraped, ScrapeRun run) {
        this.company = company;
        this.requisitionId = scraped.requisitionId();
        this.firstSeenRun = run;
        seen(scraped, run);
    }

    /**
     * The given run has listed this posting (again): refresh what Workday shows and Last Seen, and
     * reopen it if it was Closed. The Posting Date is taken when this sighting fetched one and
     * kept as it was otherwise, whether the run never asked or the detail could not be read.
     */
    void seen(ScrapedPosting scraped, ScrapeRun run) {
        WorkdayPosting listing = scraped.posting();
        this.title = listing.title();
        this.externalPath = listing.externalPath();
        this.locationText = listing.locationsText();
        this.postedOnLabel = listing.postedOn();
        if (scraped.postingDate() != null) {
            this.postingDate = scraped.postingDate();
        }
        this.publicUrl = company.getCareerSite().postingUrl(listing.externalPath());
        this.state = PostingState.OPEN;
        this.lastSeenRun = run;
    }

    /** A run read the whole Career Site and this posting was not on it. */
    void close() {
        this.state = PostingState.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getRequisitionId() {
        return requisitionId;
    }

    public String getTitle() {
        return title;
    }

    public String getExternalPath() {
        return externalPath;
    }

    public String getLocationText() {
        return locationText;
    }

    public String getPostedOnLabel() {
        return postedOnLabel;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public PostingState getState() {
        return state;
    }

    public ScrapeRun getFirstSeenRun() {
        return firstSeenRun;
    }

    public ScrapeRun getLastSeenRun() {
        return lastSeenRun;
    }
}
