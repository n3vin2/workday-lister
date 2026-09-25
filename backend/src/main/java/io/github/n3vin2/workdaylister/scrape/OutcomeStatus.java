package io.github.n3vin2.workdaylister.scrape;

/**
 * Where one Company is within a Scrape Run: waiting its turn, having its Career Site read, done
 * with everything it listed stored, failed because the Career Site could not be read (the outcome
 * then carries the reason), or cancelled before it was done, with nothing applied.
 */
public enum OutcomeStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /** Whether the run is finished with this Company: anything past waiting and reading. */
    public boolean isDone() {
        return this != QUEUED && this != IN_PROGRESS;
    }
}
