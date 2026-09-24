package io.github.n3vin2.workdaylister.scrape;

/**
 * Where one Company is within a Scrape Run: waiting its turn, having its Career Site read, done
 * with everything it listed stored, or cancelled before that, with nothing applied. Failure arrives
 * with a later ticket.
 */
public enum OutcomeStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    CANCELLED;

    /** Whether the run is finished with this Company: anything past waiting and reading. */
    public boolean isDone() {
        return this != QUEUED && this != IN_PROGRESS;
    }
}
