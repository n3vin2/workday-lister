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
 * finished keep their results; the Company being read is cancelled with nothing applied, the
 * Companies not yet reached are cancelled untouched, and the run is recorded as cancelled.
 *
 * <p>An error on one Company is logged and the run moves on to the next, so one bad Career Site
 * does not cost the rest. Recording that failure on the Company and its outcome, pacing and retry
 * arrive with a later ticket; until then a Company that failed keeps the status it had when the
 * error struck.
 */
@Component
class ScrapeRunner {

    private static final Logger log = LoggerFactory.getLogger(ScrapeRunner.class);

    private final ScrapeRecorder recorder;
    private final WorkdayClient workday;

    /** The run the worker is executing or about to execute; null while idle. */
    private final AtomicReference<Long> activeRunId = new AtomicReference<>();

    /** Whether the user has asked the active run to stop. */
    private volatile boolean cancelRequested;

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
        cancelRequested = false;
        activeRunId.set(runId);
        worker.execute(() -> execute(runId));
    }

    /** The id of the active run, or empty while the worker is idle. */
    Optional<Long> activeRun() {
        return Optional.ofNullable(activeRunId.get());
    }

    /**
     * Asks the active run to stop at its next check, between Companies or between pages. Returns
     * the id of the run asked, or empty when the worker is idle and there is nothing to cancel.
     */
    Optional<Long> requestCancel() {
        Optional<Long> active = activeRun();
        if (active.isPresent()) {
            cancelRequested = true;
        }
        return active;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private void execute(long runId) {
        try {
            for (long outcomeId : recorder.outcomeIds(runId)) {
                if (cancelRequested) {
                    break;
                }
                scrape(runId, outcomeId);
            }
            if (cancelRequested) {
                recorder.cancel(runId);
            } else {
                recorder.close(runId);
            }
        } catch (RuntimeException e) {
            log.error("Scrape Run {} stopped", runId, e);
        } finally {
            cancelRequested = false;
            activeRunId.compareAndSet(runId, null);
        }
    }

    /**
     * One Company's turn: mark it in progress, read its Career Site, store what was listed; or, if
     * the run was cancelled part-way through the pages, store nothing and mark it cancelled.
     */
    private void scrape(long runId, long outcomeId) {
        try {
            CareerSite site = recorder.begin(outcomeId);
            readCareerSite(site)
                    .ifPresentOrElse(
                            listed -> recorder.record(outcomeId, listed),
                            () -> recorder.cancelCompany(outcomeId));
        } catch (RuntimeException e) {
            log.error("Scrape Run {}: Company outcome {} failed", runId, outcomeId, e);
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
    private Optional<CareerSitePostings> readCareerSite(CareerSite site) {
        List<WorkdayPosting> postings = new ArrayList<>();
        int total = 0;
        int offset = 0;
        JobPage page;
        do {
            if (cancelRequested) {
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
