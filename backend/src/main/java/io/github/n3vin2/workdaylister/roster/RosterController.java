package io.github.n3vin2.workdaylister.roster;

import io.github.n3vin2.workdaylister.scrape.RunSummary;
import io.github.n3vin2.workdaylister.scrape.ScrapeRunService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/roster}: upload a CSV that becomes the new Roster. An accepted upload starts a
 * Scrape Run over it at once, so submitting the file is a single action; while a run is active the
 * upload is refused as a whole, so the Roster never changes under a run.
 */
@RestController
@RequestMapping("/api/roster")
class RosterController {

    /**
     * The new Roster after a successful upload, and the run started over it ({@code null} when the
     * Roster is empty).
     */
    record Replaced(List<CompanySummary> companies, RunSummary run) {}

    /** Why an upload was rejected: every failing row, with nothing written. */
    record Rejected(List<RosterCsv.RowError> errors) {}

    /** Why a valid upload was refused, with nothing written: a Scrape Run is active. */
    record NotReplaced(String reason) {}

    private final RosterService rosterService;
    private final ScrapeRunService scrapeRunService;

    RosterController(RosterService rosterService, ScrapeRunService scrapeRunService) {
        this.rosterService = rosterService;
        this.scrapeRunService = scrapeRunService;
    }

    /**
     * 200 with the new Roster and its run; 400 with every bad row when the file is invalid; 409
     * when a run is active.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        RosterCsv.Result parsed =
                RosterCsv.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        if (!parsed.isValid()) {
            return ResponseEntity.badRequest().body(new Rejected(parsed.errors()));
        }
        RunSummary run;
        try {
            run =
                    scrapeRunService
                            .start(() -> rosterService.replace(parsed.entries()))
                            .map(
                                    started ->
                                            RunSummary.of(
                                                    started, scrapeRunService.outcomesOf(started)))
                            .orElse(null);
        } catch (ScrapeRunService.RunActiveException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new NotReplaced(e.getMessage()));
        }
        return ResponseEntity.ok(
                new Replaced(CompanySummary.ofAll(rosterService.companies()), run));
    }
}
