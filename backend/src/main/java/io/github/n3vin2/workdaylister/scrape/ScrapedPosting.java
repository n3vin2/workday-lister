package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.workday.WorkdayPosting;
import java.time.LocalDate;

/**
 * One Job Posting as a Scrape Run saw it: what the jobs endpoint listed and, when Workday labelled
 * it "Posted Today" or "Posted Yesterday", the Posting Date its detail reported (ADR-0002).
 *
 * @param posting the listing
 * @param postingDate the detail's {@code startDate}; {@code null} for every other label, whose
 *     detail the run never asks for, and when the detail could not be read
 */
record ScrapedPosting(WorkdayPosting posting, LocalDate postingDate) {

    /** The requisition ID that identifies the posting within its Career Site. */
    String requisitionId() {
        return posting.requisitionId();
    }
}
