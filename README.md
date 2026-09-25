# Workday Lister

A single-user tool that reads a Roster of Companies' Workday Career Sites from a CSV, scrapes every
Job Posting from each, and shows which postings appeared today. Vocabulary is defined in
[CONTEXT.md](CONTEXT.md); design decisions live in [docs/adr](docs/adr); code conventions in
[CODING_STANDARDS.md](CODING_STANDARDS.md).

## Development setup

Prerequisites: Docker, JDK 21, Node 22 or newer. Maven is provided by the wrapper.

Three commands, in three terminals:

```sh
docker compose up -d                # 1. MySQL 8 on localhost:3306
cd backend && ./mvnw spring-boot:run   # 2. Spring Boot on http://localhost:8080 (Flyway migrates on startup)
cd frontend && npm install && npm run dev   # 3. Vite on http://localhost:5173, proxying /api to 8080
```

Open http://localhost:5173. The Roster screen shows an upload prompt until you upload a CSV. An
accepted upload starts a Scrape Run at once, and the screen follows it live: how many Companies are
done, the time elapsed, and each Company going from "Queued" through "In progress" to "Succeeded".
When the run ends, click a Company to see its Today's Postings, or every Open posting with "Show
all Open postings" ticked. If the Roster does not load, check that the backend is running (its
health check is at http://localhost:8080/api/health).

## Roster CSV

The Roster is defined by a CSV with a header row and two columns, `company,url`:

```csv
company,url
NVIDIA,https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite
M&T Bank,https://mtb.wd5.myworkdayjobs.com/MTB
```

Any Career Site URL is accepted as pasted from a browser: a locale segment such as
`/en-US/`, a job or details path, and a query string are all ignored, and rows that point at the
same Career Site (same tenant, pod, and site name) collapse to one Company. Fields may be quoted
with double quotes. Each upload replaces the whole Roster: Companies whose Career Site is already
present keep their id and take the new name, and Companies missing from the file are deleted.
An invalid file is rejected as a whole, with every bad row listed by line number and reason.

The API behind the screen is `POST /api/roster` (multipart field `file`) and `GET /api/companies`.

If another MySQL already listens on 3306, export `MYSQL_PORT=3307` (any free port) before running
commands 1 and 2; Compose and the backend both read it.

## Scrape Runs

A Scrape Run walks the Roster one Company at a time on a single background thread and stores every
Job Posting each Career Site lists. A successful upload starts one; "Scrape now" on the Roster
screen starts another over the current Roster. Starting a run returns immediately.

Only one run is active at a time. While one is, "Scrape now" and uploads are refused with a
`409` and the screen says a run is in progress, so the Roster never changes under a run (a CSV
that fails validation is still rejected with a `400` first). The Roster screen polls the active
run every 2 seconds, refreshing the Company list on each poll, and stops polling as soon as no run
is active. Cancel asks the run to stop; it checks between Companies and between requests, so it
stops within one Workday request. Companies it finished keep their Job Postings and Company
Outcome, the Company it was reading is marked cancelled with none of that Career Site's listing
applied, the Companies it had not reached are left as they were, and the run is recorded as
cancelled.

For each Company the run reads Workday's jobs endpoint in pages of 20 ([ADR-0001](docs/adr/0001-workday-cxs-json-endpoint.md)),
stopping when the offset reaches the total the first page reported, at the first short page, or at
Workday's cap of 2,000 postings. A Career Site that reports 2,000 is flagged truncated on both
screens: its count is a floor. Postings are identified within a Company by the requisition ID that
ends the Workday path; a posting listed for the first time records this run as First Seen, and
every listed posting records it as Last Seen.

Once everything a Career Site lists has been read, every posting it listed is Open and every
stored posting it no longer listed is Closed: kept with everything it had when last seen, hidden
from the Company screen until "Include Closed postings" is ticked (where it carries a Closed
marker), and left out of the Roster's Open count. A Closed posting the Career Site lists again is
Open again and keeps its First Seen. A Company whose Career Site could not be read keeps every
posting as it was. For a truncated Career Site, postings beyond Workday's 2,000 cap are not
listed and so are Closed too, until they come back within it; its Open count is what Workday lists.

Requests to Workday are paced: consecutive requests are separated by `scraper.pacing-interval`
(250 ms by default), because Workday rate-limits by source IP across every Career Site. A request
Workday throttles (`429`) or fails (`5xx`) is retried up to `scraper.retry-count` times, waiting
`scraper.retry-backoff` before the first retry and twice as long before each later one, or as long
as a `Retry-After` header asks when that is longer. A Company whose Career Site still cannot be
read after the last retry, cannot be reached at all, or answers with any other error status is
marked failed with a short reason, and the run moves on to the next Company and finishes partially
failed. The Roster screen shows the reason under the failed status and offers Retry, a run over just
that Company under the same one-run-at-a-time rule as a full run. A posting whose detail cannot be
read, after the same retries, does not fail its Company: the posting is stored from the list with
no Posting Date, keeping one an earlier run stored, so it is not one of Today's Postings until a
later run reads the detail, and the failure is logged with the run, the Company and the posting's
URL. Cancel takes effect at the run's next check between Companies or requests, so a wait in
progress finishes first.

## Today's Postings

Workday's list only labels a posting's age relative to the Career Site's timezone ("Posted
Today", "Posted Yesterday", "Posted 30+ Days Ago"), so the run fetches the posting's detail, which
carries an absolute `startDate`, for postings labelled "Posted Today" or "Posted Yesterday" only,
and stores that date as the Posting Date ([ADR-0002](docs/adr/0002-posted-today-definition.md)).
Every other posting costs no extra request and has no Posting Date, and neither has a posting whose
detail could not be read (see above). A Company's Today's Postings
are its Open postings whose Posting Date equals the current date in `scraper.timezone` (default
`America/Regina`), judged when the page is loaded, so a posting stops being today's at midnight
without a re-scrape.

The Roster screen shows each Company's today's count, Open posting count, last scraped time and
status, with the Companies that have the most Today's Postings first and ties by name. The Company
screen at `/companies/:id` lists Today's Postings as cards that open the posting on Workday in a new
tab, each with its Posting Date; "Show all Open postings" reveals every Open posting instead, and
"Include Closed postings" adds the Closed ones to either view.

| Endpoint | What |
| --- | --- |
| `POST /api/runs` | Start a run over the current Roster. `202` with the run, or `409` with a `reason` when a run is active or the Roster is empty. |
| `GET /api/runs/current` | The active run's progress: Companies `done` of `total`, `startedAt`, and each Company's outcome status. `204` when no run is active. |
| `POST /api/runs/current/cancel` | Ask the active run to stop. `202` with the run, or `204` when no run is active. |
| `GET /api/runs/{id}` | The run, with its start and end time, status (`RUNNING`, `SUCCEEDED`, `PARTIALLY_FAILED`, `CANCELLED`), progress, and each Company's outcome (status, postings seen, truncated, error message). |
| `GET /api/companies` | The Roster, each Company with its today's count, ordered by that count descending and then by name. |
| `GET /api/companies/{id}?scope=today` | A Company's header plus its Today's Postings (`today` is the default) or, with `scope=all`, every Open posting; any other scope is a `400`. `?includeClosed=true` adds the Closed ones among them. Each posting carries its `postingDate` (null unless a run fetched it) and its `state`, `OPEN` or `CLOSED`. |
| `POST /api/companies/{id}/retry` | Retry: start a run over just that Company. `202` with the run, `409` with a `reason` when a run is active, or `404` when no Company has that id. |

`POST /api/roster` also answers `409` with a `reason` while a run is active. Finished runs are
stored but not displayed: there is no run history screen.

## Layout

| Path        | What                                                                                  |
| ----------- | ------------------------------------------------------------------------------------- |
| `backend/`  | Spring Boot 3, Java 21, Maven. Base package `io.github.n3vin2.workdaylister`. Spring Data JPA, Flyway, MySQL. |
| `frontend/` | React (plain JavaScript), Vite, Tailwind, React Router.                               |
| `compose.yaml` | MySQL for local development.                                                       |
| `docs/adr/` | Architecture decision records.                                                        |
| `docs/agents/` | How agents use the issue tracker, triage labels, and domain docs.                  |
| `CODING_STANDARDS.md` | How code in both parts is formatted, named, structured, and tested.         |

## Configuration

Defaults are declared on the `ScraperProperties` and `WorkdayClientProperties` records in
`backend/src/main/java/io/github/n3vin2/workdaylister/config/`. Override any of them without a
rebuild through environment variables (or an external `application.yml`):

| Property                  | Default          | Environment variable      |
| ------------------------- | ---------------- | ------------------------- |
| `scraper.pacing-interval` | `250ms`          | `SCRAPER_PACING_INTERVAL` |
| `scraper.retry-count`     | `3`              | `SCRAPER_RETRY_COUNT`     |
| `scraper.retry-backoff`   | `1s`             | `SCRAPER_RETRY_BACKOFF`   |
| `scraper.timezone`        | `America/Regina` | `SCRAPER_TIMEZONE`        |
| `workday.client.scheme`   | `https`          | `WORKDAY_CLIENT_SCHEME`   |
| `workday.client.host`     | blank (each Career Site's own host) | `WORKDAY_CLIENT_HOST` |
| `workday.client.connect-timeout` | `10s`     | `WORKDAY_CLIENT_CONNECT_TIMEOUT` |
| `workday.client.read-timeout` | `30s`        | `WORKDAY_CLIENT_READ_TIMEOUT` |

## Tests

Backend integration tests boot the full application against a Testcontainers MySQL and a WireMock
stub of Workday, with request pacing and retry backoff set to zero (one test class raises both to
measure them) and the clock pinned. Docker must be running. The
stub's canned responses under `backend/src/test/resources/wiremock/__files/workday/` were recorded
from a real Career Site; its README says when and how.

```sh
cd backend && ./mvnw test
```

Frontend component tests use Vitest, React Testing Library, and Mock Service Worker intercepting
`/api`.

```sh
cd frontend && npm test
```

## Issues

Work is tracked in GitHub Issues on `n3vin2/workday-lister`. Issue #1 is the spec.
