-- Why a Scrape Run could not read a Company's Career Site: the short reason the Roster screen
-- shows next to a failed status. On the Company it is the latest run's reason, null unless the
-- status is FAILED; on the outcome it is that run's reason, kept as the run's record.
ALTER TABLE company
    ADD COLUMN error_message VARCHAR(512) NULL;

ALTER TABLE company_outcome
    ADD COLUMN error_message VARCHAR(512) NULL;
