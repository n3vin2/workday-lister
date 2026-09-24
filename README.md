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
accepted upload starts a Scrape Run at once; reload the page to watch each Company go from "Never
scraped" through "In progress" to "Succeeded", then click a Company to see its Open postings. If the
Roster does not load, check that the backend is running (its health check is at
http://localhost:8080/api/health).

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
screen starts another over the current Roster. Starting a run returns immediately; runs queue on
the one thread, so two started back to back execute one after the other.

For each Company the run reads Workday's jobs endpoint in pages of 20 ([ADR-0001](docs/adr/0001-workday-cxs-json-endpoint.md)),
stopping when the offset reaches the total the first page reported, at the first short page, or at
Workday's cap of 2,000 postings. A Career Site that reports 2,000 is flagged truncated on both
screens: its count is a floor. Postings are identified within a Company by the requisition ID that
ends the Workday path; a posting listed for the first time records this run as First Seen, and
every listed posting records it as Last Seen. The Roster screen shows each Company's Open posting
count, last scraped time and status; the Company screen at `/companies/:id` lists every Open
posting as a card that opens the posting on Workday in a new tab.

| Endpoint | What |
| --- | --- |
| `POST /api/runs` | Start a run over the current Roster. `202` with the run, or `409` when the Roster is empty. |
| `GET /api/runs/{id}` | The run, with its start and end time, status, and each Company's outcome (status, postings seen, truncated). |
| `GET /api/companies/{id}` | A Company's header plus its Open postings. |

Runs are stored but not yet displayed: there is no run history screen. Live progress while a run
is active, cancelling, the one-run-at-a-time rule, retry and pacing, Closed postings, and Today's
Postings are tracked as separate issues.

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
| `scraper.timezone`        | `America/Regina` | `SCRAPER_TIMEZONE`        |
| `workday.client.scheme`   | `https`          | `WORKDAY_CLIENT_SCHEME`   |
| `workday.client.host`     | blank (each Career Site's own host) | `WORKDAY_CLIENT_HOST` |
| `workday.client.connect-timeout` | `10s`     | `WORKDAY_CLIENT_CONNECT_TIMEOUT` |
| `workday.client.read-timeout` | `30s`        | `WORKDAY_CLIENT_READ_TIMEOUT` |

## Tests

Backend integration tests boot the full application against a Testcontainers MySQL and a WireMock
stub of Workday, with request pacing set to zero and the clock pinned. Docker must be running. The
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
