package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import io.github.n3vin2.workdaylister.workday.JobListing;
import io.github.n3vin2.workdaylister.workday.JobPage;
import io.github.n3vin2.workdaylister.workday.WorkdayClient;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Executes Scrape Runs on the one background thread the backend owns. Runs queue on that thread,
 * and each run visits its Companies one at a time, so the load on Workday and on this machine stays
 * modest.
 *
 * <p>An error on one Company is logged and the run moves on to the next, so one bad Career Site
 * does not cost the rest. Recording that failure on the Company and its outcome, pacing and retry
 * arrive with a later ticket; until then a Company that failed keeps the status it had when the
 * error struck. A Company that leaves the Roster mid-run (a new upload) is skipped: the recorder
 * finds its outcome gone, or the database refuses the step because the row was deleted under it.
 */
@Component
class ScrapeRunner {

    private static final Logger log = LoggerFactory.getLogger(ScrapeRunner.class);

    private final ScrapeRecorder recorder;
    private final WorkdayClient workday;
    private final ExecutorService thread =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread worker = new Thread(task, "scrape-run");
                        worker.setDaemon(true);
                        return worker;
                    });

    ScrapeRunner(ScrapeRecorder recorder, WorkdayClient workday) {
        this.recorder = recorder;
        this.workday = workday;
    }

    /** Queues an opened run for execution and returns at once. */
    void launch(long runId) {
        thread.execute(() -> execute(runId));
    }

    @PreDestroy
    void shutdown() {
        thread.shutdownNow();
    }

    private void execute(long runId) {
        try {
            for (long outcomeId : recorder.outcomeIds(runId)) {
                scrape(runId, outcomeId);
            }
            recorder.close(runId);
        } catch (RuntimeException e) {
            log.error("Scrape Run {} stopped", runId, e);
        }
    }

    /** One Company's turn: mark it in progress, read its Career Site, store what was listed. */
    private void scrape(long runId, long outcomeId) {
        try {
            recorder.begin(outcomeId)
                    .ifPresent(
                            site -> {
                                Listing listing = list(site);
                                recorder.record(outcomeId, listing.postings(), listing.truncated());
                            });
        } catch (OptimisticLockingFailureException e) {
            // No entity is versioned, so this only means the row was deleted under the step: the
            // Company left the Roster while it was being read, and its outcome went with it.
            log.info(
                    "Scrape Run {}: outcome {} skipped, its Company left the Roster mid-run",
                    runId,
                    outcomeId);
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
     */
    private Listing list(CareerSite site) {
        List<JobListing> postings = new ArrayList<>();
        int total = 0;
        int offset = 0;
        JobPage page;
        do {
            page = workday.listJobs(site, offset);
            if (offset == 0) {
                total = page.total();
            }
            postings.addAll(page.postings());
            offset += WorkdayClient.PAGE_SIZE;
        } while (page.postings().size() == WorkdayClient.PAGE_SIZE
                && offset < total
                && offset < WorkdayClient.MAX_POSTINGS);
        return new Listing(postings, total >= WorkdayClient.MAX_POSTINGS);
    }

    /** Everything a Career Site listed, and whether Workday's cap cut the list short. */
    private record Listing(List<JobListing> postings, boolean truncated) {}
}
