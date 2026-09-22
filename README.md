# Workday Lister

A single-user tool that reads a Roster of Companies' Workday Career Sites from a CSV, scrapes every
Job Posting from each, and shows which postings appeared today. Vocabulary is defined in
[CONTEXT.md](CONTEXT.md); design decisions live in [docs/adr](docs/adr).

## Development setup

Prerequisites: Docker, JDK 21, Node 22 or newer. Maven is provided by the wrapper.

Three commands, in three terminals:

```sh
docker compose up -d                # 1. MySQL 8 on localhost:3306
cd backend && ./mvnw spring-boot:run   # 2. Spring Boot on http://localhost:8080 (Flyway migrates on startup)
cd frontend && npm install && npm run dev   # 3. Vite on http://localhost:5173, proxying /api to 8080
```

Open http://localhost:5173. The Roster screen shows an upload prompt until you upload a CSV; if the
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

## Layout

| Path        | What                                                                                  |
| ----------- | ------------------------------------------------------------------------------------- |
| `backend/`  | Spring Boot 3, Java 21, Maven. Base package `io.github.n3vin2.workdaylister`. Spring Data JPA, Flyway, MySQL. |
| `frontend/` | React (plain JavaScript), Vite, Tailwind, React Router.                               |
| `compose.yaml` | MySQL for local development.                                                       |
| `docs/adr/` | Architecture decision records.                                                        |
| `docs/agents/` | How agents use the issue tracker, triage labels, and domain docs.                  |

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

## Tests

Backend integration tests boot the full application against a Testcontainers MySQL and a WireMock
stub of Workday, with request pacing set to zero and the clock pinned. Docker must be running.

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
