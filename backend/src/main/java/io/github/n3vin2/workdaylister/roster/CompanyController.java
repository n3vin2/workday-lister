package io.github.n3vin2.workdaylister.roster;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/companies}: the Roster as the Roster screen lists it. */
@RestController
@RequestMapping("/api/companies")
class CompanyController {

    private final RosterService rosterService;

    CompanyController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping
    List<CompanySummary> list() {
        return rosterService.companies().stream().map(CompanySummary::of).toList();
    }
}
