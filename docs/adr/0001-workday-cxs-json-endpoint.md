# Scrape Workday through its undocumented CXS JSON endpoint, not a browser

Workday career sites are React apps whose frontend calls `POST https://{tenant}.wd{N}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs` (20 postings per page, `total` only on the first page, hard cap of 2,000 postings) and `GET .../wday/cxs/{tenant}/{site}{externalPath}` for one posting's detail. Both work with plain HTTP, no cookies or tokens, so we call them directly from Java instead of driving a headless browser: it is one to two orders of magnitude cheaper per posting and has no browser dependency. The endpoint is not a published contract; Workday could change it, and several open-source scrapers have relied on it unchanged for years. Verified live on 2026-09-21 against nvidia.wd5, mtb.wd5 and ghr.wd1.

## Consequences

- Requests must send `Content-Type: application/json` and `Accept-Language: en-US`; the `postedOn` field is a localised relative label, never a date.
- A Company with more than 2,000 postings is silently truncated and must be flagged as such.
- Rate limiting is by source IP across all tenants, so the scraper paces requests and honours `Retry-After`.
