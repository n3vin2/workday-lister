-- Open / Closed: a Job Posting is OPEN while its Career Site still lists it and CLOSED once a
-- Scrape Run that read the whole Career Site found it gone. Closed postings are kept, not deleted,
-- and a run that lists one again reopens it. Every posting stored so far was listed by the run that
-- last saw it, so it starts OPEN.
ALTER TABLE job_posting
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'OPEN';
