package io.github.n3vin2.workdaylister.scrape;

import java.util.List;

/**
 * One Job Posting as the Company screen shows it on a card: what to read, where it is, how Workday
 * labelled its age, where to apply, and which Scrape Runs first and most recently listed it.
 */
public record PostingSummary(
        String requisitionId,
        String title,
        String locationText,
        String postedOnLabel,
        String publicUrl,
        long firstSeenRunId,
        long lastSeenRunId) {

    static PostingSummary of(JobPosting posting) {
        return new PostingSummary(
                posting.getRequisitionId(),
                posting.getTitle(),
                posting.getLocationText(),
                posting.getPostedOnLabel(),
                posting.getPublicUrl(),
                posting.getFirstSeenRun().getId(),
                posting.getLastSeenRun().getId());
    }

    /** The given postings as cards, in the order given. */
    public static List<PostingSummary> ofAll(List<JobPosting> postings) {
        return postings.stream().map(PostingSummary::of).toList();
    }
}
