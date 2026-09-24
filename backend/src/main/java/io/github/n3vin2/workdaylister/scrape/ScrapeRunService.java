package io.github.n3vin2.workdaylister.scrape;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scrape Runs: starting one over the Roster, and reading one back with its per-Company outcomes. */
@Service
public class ScrapeRunService {

    private final ScrapeRecorder recorder;
    private final ScrapeRunner runner;
    private final ScrapeRunRepository runs;
    private final CompanyOutcomeRepository outcomes;

    ScrapeRunService(
            ScrapeRecorder recorder,
            ScrapeRunner runner,
            ScrapeRunRepository runs,
            CompanyOutcomeRepository outcomes) {
        this.recorder = recorder;
        this.runner = runner;
        this.runs = runs;
        this.outcomes = outcomes;
    }

    /**
     * Starts a run over the current Roster and returns as soon as it is recorded; the run itself
     * proceeds on the background thread. Empty when the Roster is empty.
     *
     * <p>Not transactional on purpose: the run must be committed before the background thread
     * looks for it, so the recorder's transaction ends before the runner is told.
     */
    public Optional<ScrapeRun> start() {
        Optional<ScrapeRun> run = recorder.open();
        run.ifPresent(opened -> runner.launch(opened.getId()));
        return run;
    }

    @Transactional(readOnly = true)
    public Optional<ScrapeRun> find(long id) {
        return runs.findById(id);
    }

    /** A run's per-Company outcomes, by Company name. */
    @Transactional(readOnly = true)
    public List<CompanyOutcome> outcomesOf(ScrapeRun run) {
        return outcomes.findAllByRunIdOrderByCompanyNameAsc(run.getId());
    }
}
