-- The Roster: one row per Company, keyed by its parsed Career Site coordinates (tenant, pod, site).
-- tenant and pod are DNS labels and are stored lowercased by the application. The site name is
-- case sensitive on Workday, so it gets a binary collation and the unique key is case sensitive too.
CREATE TABLE company (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)  NOT NULL,
    career_site_url VARCHAR(2048) NOT NULL,
    tenant          VARCHAR(63)   NOT NULL,
    pod             VARCHAR(15)   NOT NULL,
    site            VARCHAR(255)  COLLATE utf8mb4_bin NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_career_site (tenant, pod, site)
);
