-- Scrape Runs: one row per pass over the Roster. A run is RUNNING from the moment it is started and
-- SUCCEEDED once every Company has been visited; finished_at is null while it runs.
CREATE TABLE scrape_run (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    started_at  DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    status      VARCHAR(32) NOT NULL,
    PRIMARY KEY (id)
);

-- What one Scrape Run did with one Company, keyed by (run, Company): QUEUED when the run starts,
-- IN_PROGRESS while its Career Site is being read, SUCCEEDED with the number of postings seen.
-- A Company that leaves the Roster takes its outcomes with it.
CREATE TABLE company_outcome (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    scrape_run_id BIGINT      NOT NULL,
    company_id    BIGINT      NOT NULL,
    status        VARCHAR(32) NOT NULL,
    started_at    DATETIME(6) NULL,
    finished_at   DATETIME(6) NULL,
    postings_seen INT         NOT NULL DEFAULT 0,
    truncated     BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_outcome_run_company (scrape_run_id, company_id),
    CONSTRAINT fk_company_outcome_run
        FOREIGN KEY (scrape_run_id) REFERENCES scrape_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_outcome_company
        FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE
);

-- Job Postings, keyed within a Company by the requisition ID that ends the Workday external path
-- (ADR-0001). first_seen_run_id and last_seen_run_id are the Scrape Runs that first and most
-- recently listed the posting. A Company that leaves the Roster takes its postings with it.
CREATE TABLE job_posting (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    requisition_id    VARCHAR(255)  NOT NULL,
    title             VARCHAR(1024) NOT NULL,
    external_path     VARCHAR(1024) NOT NULL,
    location_text     VARCHAR(1024) NULL,
    posted_on_label   VARCHAR(255)  NULL,
    public_url        VARCHAR(2048) NOT NULL,
    first_seen_run_id BIGINT        NOT NULL,
    last_seen_run_id  BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_posting_company_requisition (company_id, requisition_id),
    CONSTRAINT fk_job_posting_company
        FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_posting_first_seen_run
        FOREIGN KEY (first_seen_run_id) REFERENCES scrape_run (id),
    CONSTRAINT fk_job_posting_last_seen_run
        FOREIGN KEY (last_seen_run_id) REFERENCES scrape_run (id)
);

-- What the latest Scrape Run left behind for each Company, denormalised so the Roster screen
-- needs no join: when it was scraped, how many postings are Open, and whether Workday's 2,000
-- posting cap was hit (ADR-0001).
ALTER TABLE company
    ADD COLUMN last_scraped_at DATETIME(6) NULL,
    ADD COLUMN open_count      INT         NOT NULL DEFAULT 0,
    ADD COLUMN truncated       BOOLEAN     NOT NULL DEFAULT FALSE;
