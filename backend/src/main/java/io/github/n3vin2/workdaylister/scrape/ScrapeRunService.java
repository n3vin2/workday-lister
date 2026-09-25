package io.github.n3vin2.workdaylister.scrape;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scrape Runs: starting one over the Roster or over one Company (the per-Company Retry), one at a
 * time, cancelling the active one, and reading one back with its per-Company outcomes.
 */
@Service
public class ScrapeRunService {

    /**
     * A Scrape Run is active, so another cannot start and the Roster cannot be replaced under it.
     * The message is the reason shown to the user.
     */
    public static final class RunActiveException extends RuntimeException {
        RunActiveException() {
            super(RUN_IN_PROGRESS);
        }
    }

    private static final String RUN_IN_PROGRESS =
            "A Scrape Run is in progress; wait for it to finish or cancel it";

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
     * @throws RunActiveException when a run is active: only one runs at a time
     */
    public Optional<ScrapeRun> start() {
        return start(() -> {});
    }

    /**
     * Runs {@code beforeStart} and then starts a run over the Roster it leaves, with no other start
     * able to slip in between. A Roster upload uses this to replace the Roster and start its run
     * as one step, so that while a run is active the upload does neither.
     *
     * <p>Not transactional on purpose: the run must be committed before the background thread
     * looks for it, so the recorder's transaction ends before the runner is told. Synchronised so
     * that finding the runner idle and handing it the new run is one step.
     *
     * @throws RunActiveException when a run is active, before {@code beforeStart} has run
     */
    public synchronized Optional<ScrapeRun> start(Runnable beforeStart) {
        if (runner.activeRun().isPresent()) {
            throw new RunActiveException();
        }
        beforeStart.run();
        return launch(recorder.open());
    }

    /**
     * Retry: starts a run over just one Company, so a failed Company can be scraped again without
     * re-running the whole Roster, under the same one-run-at-a-time rule as a full run. Returns as
     * soon as the run is recorded; empty when no Company has that id.
     *
     * @throws RunActiveException when a run is active
     */
    public synchronized Optional<ScrapeRun> retry(long companyId) {
        if (runner.activeRun().isPresent()) {
            throw new RunActiveException();
        }
        return launch(recorder.openFor(companyId));
    }

    private Optional<ScrapeRun> launch(Optional<ScrapeRun> opened) {
        opened.ifPresent(run -> runner.launch(run.getId()));
        return opened;
    }

    /** One run, running or finished, or empty when no run has that id. */
    @Transactional(readOnly = true)
    public Optional<ScrapeRun> find(long id) {
        return runs.findById(id);
    }

    /** The active run, the one the background thread is executing, or empty when none is. */
    @Transactional(readOnly = true)
    public Optional<ScrapeRun> current() {
        return runner.activeRun().flatMap(runs::findById);
    }

    /**
     * Asks the active run to stop; it does so at its next check, between Companies or between
     * pages, and is then recorded as cancelled. Returns that run, or empty when none is active.
     */
    @Transactional(readOnly = true)
    public Optional<ScrapeRun> cancel() {
        return runner.requestCancel().flatMap(runs::findById);
    }

    /** A run's per-Company outcomes, by Company name. */
    @Transactional(readOnly = true)
    public List<CompanyOutcome> outcomesOf(ScrapeRun run) {
        return outcomes.findAllByRunIdOrderByCompanyNameAsc(run.getId());
    }
}
