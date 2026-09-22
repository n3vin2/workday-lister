package io.github.n3vin2.workdaylister.roster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code POST /api/roster}: upload a CSV that becomes the new Roster. */
@RestController
@RequestMapping("/api/roster")
class RosterController {

    /** The new Roster after a successful upload. */
    record Replaced(List<CompanySummary> companies) {}

    /** Why an upload was rejected: every failing row, with nothing written. */
    record Rejected(List<RosterCsv.RowError> errors) {}

    private final RosterService rosterService;

    RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        RosterCsv.Result parsed =
                RosterCsv.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        if (!parsed.isValid()) {
            return ResponseEntity.badRequest().body(new Rejected(parsed.errors()));
        }
        List<CompanySummary> roster =
                rosterService.replace(parsed.entries()).stream().map(CompanySummary::of).toList();
        return ResponseEntity.ok(new Replaced(roster));
    }
}
