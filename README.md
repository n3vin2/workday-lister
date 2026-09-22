# Workday Lister

A single-user tool that reads a Roster of employers' Workday Career Sites from a CSV, scrapes every
Job Posting from each, and shows which postings appeared today. Vocabulary is defined in
[CONTEXT.md](CONTEXT.md); design decisions live in [docs/adr](docs/adr).

## Development setup

Prerequisites: Docker, JDK 21, Node 20 or newer. Maven is provided by the wrapper.

Three commands, in three terminals:

```sh
docker compose up -d                # 1. MySQL 8 on localhost:3306
cd backend && ./mvnw spring-boot:run   # 2. Spring Boot on http://localhost:8080 (Flyway migrates on startup)
cd frontend && npm install && npm run dev   # 3. Vite on http://localhost:5173, proxying /api to 8080
```

Open http://localhost:5173. The page reports whether the backend is reachable. The backend health
check is at http://localhost:8080/api/health.

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

Scrape Run tunables live in `backend/src/main/resources/application.yml` and can be overridden with
environment variables:

| Property                  | Default          | Environment variable      |
| ------------------------- | ---------------- | ------------------------- |
| `scraper.pacing-interval` | `250ms`          | `SCRAPER_PACING_INTERVAL` |
| `scraper.retry-count`     | `3`              | `SCRAPER_RETRY_COUNT`     |
| `scraper.timezone`        | `America/Regina` | `SCRAPER_TIMEZONE`        |
| `workday.client.scheme`   | `https`          | `WORKDAY_CLIENT_SCHEME`   |
| `workday.client.host`     | blank (per-tenant host) | `WORKDAY_CLIENT_HOST` |

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
