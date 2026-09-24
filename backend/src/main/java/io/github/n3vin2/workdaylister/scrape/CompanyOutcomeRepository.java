package io.github.n3vin2.workdaylister.scrape;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CompanyOutcomeRepository extends JpaRepository<CompanyOutcome, Long> {

    /** A run's outcomes in the order the run visits its Companies: by name. */
    List<CompanyOutcome> findAllByRunIdOrderByCompanyNameAsc(Long runId);

    /** A run's outcomes in one state, for example those still QUEUED when it is cancelled. */
    List<CompanyOutcome> findAllByRunIdAndStatus(Long runId, OutcomeStatus status);
}
