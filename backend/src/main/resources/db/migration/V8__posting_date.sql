-- The Posting Date: the calendar date Workday's detail endpoint reports a Job Posting went live, a
-- bare date with no timezone (ADR-0002). A Scrape Run fetches it only for postings Workday labels
-- "Posted Today" or "Posted Yesterday"; every other posting has none. Today's Postings are read by
-- this date at request time, so it is indexed.
ALTER TABLE job_posting
    ADD COLUMN posting_date DATE NULL,
    ADD KEY idx_job_posting_posting_date (posting_date);
