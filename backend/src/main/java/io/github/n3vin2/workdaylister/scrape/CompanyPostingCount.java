package io.github.n3vin2.workdaylister.scrape;

/**
 * How many of one Company's postings a grouped count matched: the row type of
 * {@link JobPostingRepository#countByStateAndPostingDateGroupedByCompany}.
 *
 * @param companyId the Company's id
 * @param count how many of its postings matched
 */
record CompanyPostingCount(Long companyId, long count) {}
