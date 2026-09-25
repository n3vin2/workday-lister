package io.github.n3vin2.workdaylister.roster;

import io.github.n3vin2.workdaylister.scrape.JobPosting;
import io.github.n3vin2.workdaylister.scrape.JobPostingService;
import io.github.n3vin2.workdaylister.scrape.PostingSummary;
import io.github.n3vin2.workdaylister.scrape.RunSummary;
import io.github.n3vin2.workdaylister.scrape.ScrapeRun;
import io.github.n3vin2.workdaylister.scrape.ScrapeRunService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/companies}: the Roster as the Roster screen lists it. {@code GET
 * /api/companies/{id}}: one Company with its Open postings, as the Company screen shows it. {@code
 * POST /api/companies/{id}/retry}: Retry, a Scrape Run over just that Company.
 */
@RestController
@RequestMapping("/api/companies")
class CompanyController {

    /** Why a Retry did not start a run: a Scrape Run is active. */
    record NotRetried(String reason) {}

    private final RosterService rosterService;
    private final JobPostingService jobPostingService;
    private final ScrapeRunService scrapeRunService;

    CompanyController(
            RosterService rosterService,
            JobPostingService jobPostingService,
            ScrapeRunService scrapeRunService) {
        this.rosterService = rosterService;
        this.jobPostingService = jobPostingService;
        this.scrapeRunService = scrapeRunService;
    }

    @GetMapping
    List<CompanySummary> list() {
        return CompanySummary.ofAll(rosterService.companies());
    }

    @GetMapping("/{id}")
    ResponseEntity<CompanyDetail> find(@PathVariable long id) {
        Optional<Company> company = rosterService.find(id);
        if (company.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<JobPosting> postings = jobPostingService.openPostingsOf(company.get());
        return ResponseEntity.ok(CompanyDetail.of(company.get(), PostingSummary.ofAll(postings)));
    }

    /**
     * Accepted (202) with the run over just this Company, which proceeds in the background; 409
     * when a run is already active, as only one runs at a time; 404 when no Company has the id.
     */
    @PostMapping("/{id}/retry")
    ResponseEntity<?> retry(@PathVariable long id) {
        try {
            return scrapeRunService
                    .retry(id)
                    .<ResponseEntity<?>>map(run -> ResponseEntity.accepted().body(summary(run)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (ScrapeRunService.RunActiveException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new NotRetried(e.getMessage()));
        }
    }

    private RunSummary summary(ScrapeRun run) {
        return RunSummary.of(run, scrapeRunService.outcomesOf(run));
    }
}
