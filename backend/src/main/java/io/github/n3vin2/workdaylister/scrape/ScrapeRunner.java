package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import io.github.n3vin2.workdaylister.workday.JobPage;
import io.github.n3vin2.workdaylister.workday.WorkdayClient;
import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes Scrape Runs on the one background thread the backend owns, one run at a time, and each
 * run visits its Companies one at a time, so the load on Workday and on this machine stays modest.
 * The runner knows which run it is executing, which is what makes a run "active" for the
 * one-run-at-a-time rule and the current-run endpoint; the database's RUNNING status is the record
 * of it, not the source.
 *
 * <p>Cancelling sets a flag the run checks between Companies and between pages. Companies already
 * finished keep their Job Postings and Company Outcome; the Company being read is cancelled with
 * nothing applied, the Companies not yet reached are cancelled untouched, and the run is recorded
 * as cancelled.
 *
 * <p>A Company whose Career Site cannot be read, once the Workday client has retried what it will,
 * is marked failed with the reason and the run moves on to the next, so one bad Career Site does
 * not cost the rest; the run then finishes partially failed. Pacing and retry are the client's.
 */
@Component
class ScrapeRunner {

    /** The run being executed, or about to be, and whether the user has asked it to stop. */
    private static final class ActiveRun {

        private final long id;
        private volatile boolean cancelRequested;

        ActiveRun(long id) {
            this.id = id;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ScrapeRunner.class);

    private final ScrapeRecorder recorder;
    private final WorkdayClient workday;

    /** Null while the worker is idle. */
    private final AtomicReference<ActiveRun> active = new AtomicReference<>();

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "scrape-run");
                        thread.setDaemon(true);
                        return thread;
                    });

    ScrapeRunner(ScrapeRecorder recorder, WorkdayClient workday) {
        this.recorder = recorder;
        this.workday = workday;
    }

    /**
     * Makes an opened run the active one and queues it for execution, returning at once. The caller
     * ensures no run is active; the runner is idle again once the run has been recorded as
     * finished or cancelled.
     */
    void launch(long runId) {
        ActiveRun run = new ActiveRun(runId);
        active.set(run);
        worker.execute(() -> execute(run));
    }

    /** The id of the active run, or empty while the worker is idle. */
    Optional<Long> activeRun() {
        return Optional.ofNullable(active.get()).map(run -> run.id);
    }

    /**
     * Asks the active run to stop at its next check, between Companies or between pages. Returns
     * the id of the run asked, or empty when the worker is idle and there is nothing to cancel.
     */
    Optional<Long> requestCancel() {
        ActiveRun run = active.get();
        if (run == null) {
            return Optional.empty();
        }
        run.cancelRequested = true;
        return Optional.of(run.id);
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private void execute(ActiveRun run) {
        try {
            for (long outcomeId : recorder.outcomeIds(run.id)) {
                if (run.cancelRequested) {
                    break;
                }
                scrape(run, outcomeId);
            }
            if (run.cancelRequested) {
                recorder.cancel(run.id);
            } else {
                recorder.close(run.id);
            }
        } catch (RuntimeException e) {
            log.error("Scrape Run {} stopped", run.id, e);
        } finally {
            active.compareAndSet(run, null);
        }
    }

    /**
     * One Company's turn: mark it in progress, read its Career Site, store what was listed; or, if
     * the run was cancelled part-way through the pages, store nothing and mark it cancelled; or,
     * if the Career Site could not be read, store nothing and mark it failed with the reason.
     */
    private void scrape(ActiveRun run, long outcomeId) {
        try {
            CareerSite site = recorder.begin(outcomeId);
            readCareerSite(run, site)
                    .ifPresentOrElse(
                            listed -> recorder.record(outcomeId, listed),
                            () -> recorder.cancelCompany(outcomeId));
        } catch (WorkdayClient.RequestFailedException e) {
            log.info(
                    "Scrape Run {}: Company outcome {} failed: {}",
                    run.id,
                    outcomeId,
                    e.getMessage());
            recorder.fail(outcomeId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Scrape Run {}: Company outcome {} failed", run.id, outcomeId, e);
            recorder.fail(outcomeId, "Unexpected error; see the backend log");
        }
    }

    /**
     * Pages through a Career Site in pages of {@link WorkdayClient#PAGE_SIZE}. The total is read
     * from the first page only (later pages report 0). Paging stops once the offset reaches that
     * total, because Workday answers a request past the end with a full page again rather than an
     * empty one; at the first short page, because the Career Site may have shrunk since the first
     * page was read; and at {@link WorkdayClient#MAX_POSTINGS}, past which Workday lists nothing. A
     * Career Site reporting that many is truncated: the count is a floor, not the truth.
     *
     * <p>Empty when cancellation was requested before a page was read: the Career Site's listing
     * is then incomplete and none of it is to be applied.
     */
    private Optional<CareerSitePostings> readCareerSite(ActiveRun run, CareerSite site) {
        List<WorkdayPosting> postings = new ArrayList<>();
        int total = 0;
        int offset = 0;
        JobPage page;
        do {
            if (run.cancelRequested) {
                return Optional.empty();
            }
            page = workday.listJobs(site, offset);
            if (offset == 0) {
                total = page.total();
            }
            postings.addAll(page.postings());
            offset += WorkdayClient.PAGE_SIZE;
        } while (page.postings().size() == WorkdayClient.PAGE_SIZE
                && offset < total
                && offset < WorkdayClient.MAX_POSTINGS);
        return Optional.of(
                new CareerSitePostings(postings, total >= WorkdayClient.MAX_POSTINGS));
    }
}
