package io.github.n3vin2.workdaylister.scrape;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/runs}: "Scrape now", a Scrape Run over the current Roster. {@code GET
 * /api/runs/{id}}: a run and its per-Company outcomes.
 */
@RestController
@RequestMapping("/api/runs")
class ScrapeRunController {

    /** Why a run was not started. */
    record NotStarted(String reason) {}

    private static final String EMPTY_ROSTER = "The Roster is empty; upload a CSV first";

    private final ScrapeRunService scrapeRunService;

    ScrapeRunController(ScrapeRunService scrapeRunService) {
        this.scrapeRunService = scrapeRunService;
    }

    /** Accepted (202) with the run, which proceeds in the background; 409 when there is nothing to run over. */
    @PostMapping
    ResponseEntity<?> start() {
        return scrapeRunService
                .start()
                .<ResponseEntity<?>>map(run -> ResponseEntity.accepted().body(summary(run)))
                .orElseGet(
                        () ->
                                ResponseEntity.status(HttpStatus.CONFLICT)
                                        .body(new NotStarted(EMPTY_ROSTER)));
    }

    @GetMapping("/{id}")
    ResponseEntity<RunSummary> find(@PathVariable long id) {
        return scrapeRunService
                .find(id)
                .map(run -> ResponseEntity.ok(summary(run)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private RunSummary summary(ScrapeRun run) {
        return RunSummary.of(run, scrapeRunService.outcomesOf(run));
    }
}
