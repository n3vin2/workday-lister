package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import io.github.n3vin2.workdaylister.workday.JobPage;
import io.github.n3vin2.workdaylister.workday.WorkdayClient;
import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Cancelling sets a flag the run checks between Companies and between requests, a page or a
 * posting's detail alike. Companies already finished keep their Job Postings and Company Outcome;
 * the Company being read is cancelled with nothing applied, the Companies not yet reached are
 * cancelled untouched, and the run is recorded as cancelled.
 *
 * <p>A Company whose Career Site cannot be read, once the Workday client has retried what it will,
 * is marked failed with the reason and the run moves on to the next, so one bad Career Site does
 * not cost the rest; the run then finishes partially failed. Pacing and retry are the client's.
 *
 * <p>A posting's detail that cannot be read, once the client has retried what it will, costs
 * the Company nothing but that posting's Posting Date: the posting is stored from its listing
 * with none (keeping any an earlier run stored), the failure is logged with the run, the
 * Company Outcome and the posting's URL, and the Company succeeds. Without a Posting Date the
 * posting is not one of Today's Postings; the next run asks for the detail again while Workday
 * still labels the posting "Posted Today" or "Posted Yesterday".
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
     * Asks the active run to stop at its next check, between Companies or between requests (a page
     * or a posting's detail). Returns the id of the run asked, or empty when the worker is idle and
     * there is nothing to cancel.
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
     * One Company's turn: mark it in progress, read its Career Site and the details it warrants,
     * store what was listed; or, if the run was cancelled part-way through those requests, store
     * nothing and mark it cancelled; or, if the Career Site could not be read, store nothing and
     * mark it failed with the reason.
     */
    private void scrape(ActiveRun run, long outcomeId) {
        try {
            CareerSite site = recorder.begin(outcomeId);
            readCareerSite(run, outcomeId, site)
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
     * <p>Empty when cancellation was requested before a page or a detail was read: the Career
     * Site's listing is then incomplete and none of it is to be applied.
     */
    private Optional<CareerSitePostings> readCareerSite(
            ActiveRun run, long outcomeId, CareerSite site) {
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
        boolean truncated = total >= WorkdayClient.MAX_POSTINGS;
        return withPostingDates(run, outcomeId, site, postings)
                .map(scraped -> new CareerSitePostings(scraped, truncated));
    }

    /**
     * The listed postings, each with its Posting Date where its label warrants a detail request
     * and the detail could be read. A posting Workday lists twice across pages (its paging shifts
     * as postings appear) is kept once, so it costs at most one request. Empty when cancellation
     * was requested before a detail was read: the flag is checked before each detail request as
     * it is before each page.
     */
    private Optional<List<ScrapedPosting>> withPostingDates(
            ActiveRun run, long outcomeId, CareerSite site, List<WorkdayPosting> listed) {
        Map<String, WorkdayPosting> distinct = new LinkedHashMap<>();
        for (WorkdayPosting posting : listed) {
            distinct.putIfAbsent(posting.requisitionId(), posting);
        }
        List<ScrapedPosting> scraped = new ArrayList<>(distinct.size());
        for (WorkdayPosting posting : distinct.values()) {
            if (run.cancelRequested && posting.postedTodayOrYesterday()) {
                return Optional.empty();
            }
            scraped.add(
                    new ScrapedPosting(posting, postingDateOf(run, outcomeId, site, posting)));
        }
        return Optional.of(scraped);
    }

    /**
     * The Posting Date of a posting labelled "Posted Today" or "Posted Yesterday", from its detail;
     * {@code null} for any other label, which costs no request (ADR-0002), and {@code null} when
     * the detail could not be read once the client had retried what it will. That is logged, and
     * costs the posting its Posting Date rather than the Company its run: the listing is still
     * complete, so what the Career Site lists can be stored and nothing is Closed by mistake.
     * A request abandoned because this thread is being shut down is not the Career Site's
     * fault, and still fails the Company rather than store it half-read as succeeded.
     */
    private LocalDate postingDateOf(
            ActiveRun run, long outcomeId, CareerSite site, WorkdayPosting posting) {
        if (!posting.postedTodayOrYesterday()) {
            return null;
        }
        try {
            return workday.fetchDetail(site, posting.externalPath()).startDate();
        } catch (WorkdayClient.RequestFailedException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            log.warn(
                    "Scrape Run {}: Company outcome {}: detail of {} could not be read ({}); the"
                            + " posting is stored without a Posting Date",
                    run.id,
                    outcomeId,
                    site.postingUrl(posting.externalPath()),
                    e.getMessage());
            return null;
        }
    }
}
