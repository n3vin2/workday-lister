package io.github.n3vin2.workdaylister.roster;

import io.github.n3vin2.workdaylister.scrape.JobPosting;
import io.github.n3vin2.workdaylister.scrape.JobPostingService;
import io.github.n3vin2.workdaylister.scrape.PostingSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/companies}: the Roster as the Roster screen lists it. {@code GET
 * /api/companies/{id}}: one Company with its Open postings, as the Company screen shows it.
 */
@RestController
@RequestMapping("/api/companies")
class CompanyController {

    private final RosterService rosterService;
    private final JobPostingService jobPostingService;

    CompanyController(RosterService rosterService, JobPostingService jobPostingService) {
        this.rosterService = rosterService;
        this.jobPostingService = jobPostingService;
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
}
