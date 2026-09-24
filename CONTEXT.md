# Workday Lister

A single-user tool that reads a roster of employers' Workday career sites from a CSV, scrapes every job posting from each, and shows which postings appeared today.

## Language

**Company**:
An employer the user is tracking. Has a display name (from the CSV) and exactly one Career Site.
_Avoid_: Employer, tenant, organization

**Career Site**:
The public Workday-hosted jobs page for one Company, identified by a URL of the form `{tenant}.wd{N}.myworkdayjobs.com/{site}`. The unit the scraper visits.
_Avoid_: Workday URL, job board, careers page

**Job Posting**:
One open position listed on a Career Site, as Workday presents it. Identified within a Career Site by its requisition ID.
_Avoid_: Job listing, job, vacancy, req

**Roster**:
The set of Companies the user is currently tracking, loaded from the uploaded CSV.
_Avoid_: Company list, watchlist, CSV

**Scrape Run**:
One pass over the whole Roster that fetches every Job Posting from every Career Site, one Company at a time. Has per-Company status.
_Avoid_: Scrape, crawl, job, sync, refresh

**Company Outcome**:
What one Scrape Run did with one Company: when it started and finished reading the Career Site, how many Job Postings it saw, whether the list was truncated, and its status.
_Avoid_: Result, run item, per-Company status row

## Posting lifecycle

**Posting Date**:
The calendar date Workday records as the day a Job Posting went live. A bare date with no time or timezone.
_Avoid_: Posted on, start date, created date

**Today's Postings**:
The Job Postings of one Company whose Posting Date equals the current calendar date in Saskatchewan (America/Regina).
_Avoid_: New jobs, today's jobs, recent postings

**Open / Closed**:
A Job Posting is Open while its Career Site still lists it, and Closed once a Scrape Run finds it gone. Closed postings are kept, not deleted.
_Avoid_: Active/inactive, expired, removed, deleted

**First Seen / Last Seen**:
The Scrape Runs that first and most recently observed a Job Posting. Independent of Posting Date.
_Avoid_: Created at, updated at, discovered
