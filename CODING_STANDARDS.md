# Workday Lister Coding Standards

Conventions for this repo. Follow them when writing or editing code in `backend/` or `frontend/`.
They are adapted from the author's Safewalk coding style (`~/claude_readme/CODING_STYLE.md` on the
author's machine). Where that guide and the code here disagree, the rule below is what this repo's
code does, and [section 5](#5-where-this-repo-differs-from-the-safewalk-style) lists every such
divergence so the choice can be revisited in one place.

Nothing here is enforced by tooling: there is no Prettier or ESLint config, no Checkstyle, and no
`.editorconfig`. This file is the source of truth.

| Part | Path | Stack |
|---|---|---|
| Backend | `backend/` | Spring Boot 3.5, Java 21, Spring Data JPA, Flyway, MySQL 8. Tests: JUnit 5, AssertJ, Testcontainers, WireMock |
| Frontend | `frontend/` | React 19 (plain JavaScript), Vite 7, react-router 7, Tailwind v4. Tests: Vitest, React Testing Library, MSW |

---

## 1. Rules that apply everywhere

- **Spaces, never tabs.** Java indents 4; JavaScript, JSX, YAML, and SQL indent 2. Do not mix widths within a file.
- **K&R braces** in every language: opening brace on the same line, `} else {` on one line.
- **Lines under about 100 characters.** Javadoc prose, JSX text, and test fixtures such as CSV text blocks may run longer rather than be broken mid-sentence.
- **Domain vocabulary comes from `CONTEXT.md`.** Use its terms, capitalised as it writes them (Roster, Company, Career Site, Today's Postings), in identifiers, comments, test names, UI copy, and API paths. Cite decisions by number: `ADR-0001`.
- **Comments state intent, not mechanics.** Java: Javadoc on every class, record, and enum, and on public methods; one-line `/** ... */` on fields that need it. JavaScript: a `//` line at the top of a module saying what it is for, and `/** ... */` above exported functions. No `TODO` markers; unfinished work is a GitHub issue.
- **Canadian/British spelling** in prose and in identifiers we coin (`normalised`, `recognise`). Library APIs keep their own spelling (`color`).
- **Configuration has a default and an environment override.** Backend tunables are `@ConfigurationProperties` records with `@DefaultValue`, listed in the README's Configuration table. The frontend has no environment variables: it calls `/api` relatively and Vite proxies it.
- **Constants are named when something else depends on their value**: database column widths, user-facing messages that tests assert on, shared suffixes. Small one-off literals stay inline.
- **Tests are part of every change.** Backend behaviour is tested through the HTTP API against a real MySQL; frontend behaviour through the rendered screen against a mocked `/api`. See each section.
- **Logging.** SLF4J via a `private static final Logger log = LoggerFactory.getLogger(...)` in Java (`ScrapeRunner`), `console.*` in the frontend. `error` for what needs a human, `info` for what is expected but worth a trace.

---

## 2. `backend/` (Spring Boot)

### Package layout

Root package is `io.github.n3vin2.workdaylister`. Packages are **features**, not layers: a feature package holds its controllers, service, repository, entity, and records together.

```
config/     ClockConfig, ScraperProperties, WorkdayClientProperties
roster/     Company, CareerSite, CompanyStatus, CompanyRepository, RosterService,
            CompanyController, RosterController, RosterCsv, RosterEntry, CompanySummary, CompanyDetail
scrape/     ScrapeRun, CompanyOutcome, JobPosting and their statuses and repositories; ScrapeRecorder
            (one transaction per step), ScrapeRunner (the background thread), ScrapeRunService,
            JobPostingService, ScrapeRunController, RunSummary, PostingSummary, ScrapedPosting,
            CareerSitePostings, CompanyPostingCount
workday/    WorkdayClient (the ADR-0001 adapter interface), HttpWorkdayClient, JobPage, WorkdayPosting, JobDetail
```

- Default to package-private. Make a type or member `public` when another package, or a test in another package, needs it.
- Dependencies point inward: `scrape` uses `roster` and `workday`; `workday` and `config` use `roster`'s `CareerSite` as a value type. `roster`'s controllers are the composition point: they alone may call `scrape`'s services (to start a run after an upload, to list a Company's postings). Nothing else in `roster` depends on `scrape`.
- Tests mirror the main packages. The shared `IntegrationHarness`, `PinnedClockConfig`, and `WorkdayPages` sit at the root test package.

### Types

- **Records** for request and response bodies (`CompanySummary`, `RosterController.Replaced`), parse results (`RosterCsv.Result`, `RosterCsv.RowError`), value objects (`CareerSite`, which is also the `@Embeddable`), what one step hands the next (`ScrapedPosting`, `CareerSitePostings`), query row types (`CompanyPostingCount`), and configuration (`ScraperProperties`).
- **JPA entities are classes** with a `protected` no-arg constructor for Hibernate, a domain constructor that takes what the entity is built from (`Company(RosterEntry)`), getters, and package-private mutators named for the domain operation (`adopt`). No setters.
- **Static factories** over constructors at call sites: `CompanySummary.of(company)`, `CompanySummary.ofAll(companies)`, `CareerSite.parse(url)`, `RosterCsv.parse(text)`.
- A parse or validation result is **either/or, never both**: `Result(entries, errors)` with `isValid()`, entries empty when errors are present.
- Stateless helpers are `public final class` with a private constructor (`RosterCsv`).
- No Lombok.

### Naming

- Class suffixes: `*Controller`, `*Service` (one concrete class, no interface), `*Repository`, `*Properties`, `*Config`, `*Summary` for list-row responses, `*Detail` for a one-thing response, `*Recorder` for a bean that writes one feature's state a transaction at a time, `*Runner` for a bean that owns a thread, `*Test`. Endpoint-specific bodies are records nested in the controller and named for the outcome (`Replaced`, `Rejected`).
- Methods: camelCase. Entities use JavaBean getters (`getName`); records and services use bare accessors (`name()`, `companies()`); operations are verbs (`replace`, `adopt`, `parse`).
- Constants `UPPER_SNAKE`; enum constants `UPPER_SNAKE` (`NEVER_SCRAPED`).
- Endpoints: `/api/<plural-noun>` for collections (`/api/companies`, `/api/runs`) and `/api/<plural-noun>/{id}` for one of them; `/api/roster` is singular because there is exactly one Roster. Health is `/api/health` via the actuator base path.

### Formatting

- 4-space indent; **8-space continuation** for wrapped expressions, arguments, and record components.

```java
String resolvedHost =
        (host == null || host.isBlank())
                ? tenant + "." + pod + "." + PUBLIC_HOST_SUFFIX
                : host;
```

```java
public record CareerSite(
        @Column(nullable = false, length = 63) String tenant,
        @Column(nullable = false, length = 15) String pod,
        @Column(nullable = false) String site) {
```

- Annotations on classes, fields, and methods go on their own line, one per line. Parameter and record-component annotations stay inline.
- Imports: no wildcards. Static imports first, a blank line, then one block in ASCII order (`com.*`, `io.*`, `jakarta.*`, `java.*`, `org.*`), no blank lines between groups.
- One blank line after the class's opening brace, between fields, and between methods.
- Long messages that tests assert on are `private static final String` constants; the parser's column widths are `private static final int` constants with a comment naming the migration they mirror.

### Dependency injection

- Constructor injection with a package-private constructor and `private final` fields. Spring finds the single constructor; no `@Autowired` in main code.

```java
@Service
public class RosterService {

    private final CompanyRepository companies;

    RosterService(CompanyRepository companies) {
        this.companies = companies;
    }
```

### Controllers

- `@RestController` with a class-level `@RequestMapping("/api/...")`; bare `@GetMapping` / `@PostMapping` on methods. The class and its handler methods are package-private.
- When the status is always 200, return the body directly (`List<CompanySummary> list()`). When it varies, return `ResponseEntity<?>` built with Spring's factories: `ok(...)`, `accepted()` for work that continues in the background, `badRequest().body(...)`, `status(HttpStatus.CONFLICT).body(...)`, `notFound().build()`. A refusal carries a one-field record saying why (`NotStarted(reason)`).
- Validation failures are a 400 with a body that lists **every** error (`Rejected(errors)`), so the user fixes the file in one pass. They are not exceptions.
- Query parameters are `@RequestParam(defaultValue = ...)` with the accepted values as `private static final String` constants; a value the endpoint does not know is a 400 with a one-field record saying why (`CompanyController.Rejected(reason)`).
- Controllers map entities to records (`CompanySummary.ofAll(...)`); services return entities.
- Uploads: `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` with `@RequestParam("file") MultipartFile file`.

### Services

- `@Service`. `@Transactional` from `org.springframework.transaction.annotation` on every public method, `readOnly = true` on reads. A whole replace-style operation is one transaction.
- The one exception is a method that must act only after its transaction has committed (`ScrapeRunService.start` hands the run to the background thread): it is not transactional itself and delegates each step to a transactional method on another bean (`ScrapeRecorder`), never to one on its own class, which the proxy would not see. Work that talks to Workday happens between transactions, not inside one.
- Methods are named for the domain operation, not the persistence call: `replace`, `companies`.

### Repositories

- `interface *Repository extends JpaRepository<Entity, Long>`, package-private, with derived queries such as `findAllByOrderByNameAsc()`. A grouped count that a derived query cannot express is an `@Query` JPQL constructor expression into a package-private record (`CompanyPostingCount`).

### Entities and schema

- Schema changes are Flyway migrations `V<n>__<snake_name>.sql` under `src/main/resources/db/migration`; `spring.jpa.hibernate.ddl-auto` is `validate` and `open-in-view` is `false`. Never edit an applied migration.
- SQL: uppercase keywords, snake_case identifiers, columns aligned, unique keys named `uk_<table>_<what>`, a `--` header comment explaining the table's key.
- Entities: `@Entity @Table(name = "snake_case")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id`, `@Column(nullable = false, length = N)` mirroring the migration, `@Enumerated(EnumType.STRING)` for enums, `@Embedded` for value objects. Boxed types for the id and for nullable columns (`Long id`, `Instant finishedAt`); primitives for `NOT NULL` numbers and flags (`int openCount`, `boolean truncated`). `@ManyToOne(fetch = FetchType.LAZY)` with an explicit `@JoinColumn`, eager only where every reader needs the target (`CompanyOutcome.company`).
- An entity that two writers update at once for different reasons (`Company`: an upload renames it, a run records results) is `@DynamicUpdate`, so each writes only its own columns and neither overwrites the other. Every step of background work reloads its entities by id inside its own transaction; nothing detached is saved back.
- Foreign keys carry `ON DELETE CASCADE` where the child is meaningless without the parent (a Company's postings and outcomes), and no cascade where history must survive (a posting's First Seen run).

### Exceptions

- A domain exception is nested in the type it belongs to (`CareerSite.InvalidUrlException extends RuntimeException`), and its message is the reason shown to the user. Callers catch it and turn it into an error entry. No `@ControllerAdvice` or `@ResponseStatus` exists yet.

### Configuration

- `application.yml`, not `.properties`. Environment placeholders with defaults: `${MYSQL_PORT:3306}`.
- Tunables are `@ConfigurationProperties(prefix = "...")` records with `@DefaultValue`, found by `@ConfigurationPropertiesScan`. Adding one means adding its row to the README's Configuration table.
- Time comes from the injected `Clock` bean (`ClockConfig`), never `LocalDate.now()` bare.

### Tests

- Integration tests extend `IntegrationHarness`: the full context on a random port, a Testcontainers MySQL via `@ServiceConnection`, a WireMock `workday` stub standing in for every Career Site, pacing and retry backoff set to zero, and the clock pinned by `PinnedClockConfig` (`PINNED_NOW`, and `PINNED_TODAY` for its date in Regina). Both servers start once per JVM in a static block and are shared across classes. Before each test the harness waits for the last Scrape Run it started, resets the stub, gives every Career Site an empty page and every posting's detail a far-past Posting Date as fallback stubs (so a run always finishes and nothing is today's by accident), and empties the Roster.
- Drive the application **only** through `api` (a `TestRestTemplate`) and the `workday` stub. No repository or service calls from tests. Background work is observed by polling the API with Awaitility (`awaitRunFinished`) and Workday traffic through the stub's request journal (`workday.findAll`, `getAllServeEvents`), never through pacing or timing, with one exception: a lower bound on the time between two journal entries (`gaps`), which the client's wait guarantees, is how pacing, backoff and `Retry-After` are asserted (`PacingTest`, in a context of its own where both are long enough to measure). To catch a run mid-Company, give that Company's stub the `HOLD` transformer and call `holdResponses()`; the response goes out only on `releaseHeldResponses()`. No test races a delay against a deadline.
- Recorded Workday responses live under `src/test/resources/wiremock/__files/workday/` with a README naming the capture; tests that need a particular `total`, page length, label, or Posting Date build bodies in the same shape with `WorkdayPages` (`jobsPage`, `listing`, `postings`, `detail`) and stub them through the harness (`stubJobs`, `stubJobsFromRecording`, `stubDetail`). The harness's `texts`, `ints`, `longs`, and `booleans` read one field of every element of a JSON array.
- One test class per feature, `*Test`. Method names are sentences in camelCase with no `test` or `should` prefix: `reuploadKeepsMatchingIdsAdoptsNewNamesAndDeletesAbsentCompanies`.
- AssertJ `assertThat`; a blank line separates act from assert; CSV fixtures are text blocks; private static helpers (`errors`, `names`) sit at the bottom of the class.
- Configuration records are tested with `ApplicationContextRunner` and a nested `static class Config` carrying `@EnableConfigurationProperties`.
- `./mvnw test` needs Docker running.

---

## 3. `frontend/` (React + Vite + Tailwind)

### Layout

```
src/main.jsx                    StrictMode + BrowserRouter + App
src/App.jsx                     Routes only
src/pages/<Name>Page.jsx        one routed screen
src/pages/<Name>Page.test.jsx   its tests, beside it
src/components/<Name>.jsx       a piece more than one screen renders (Notice)
src/api/<resource>.js           every fetch call for one resource
src/format.js                   formatting both screens share (formatDateTime, formatStatus)
src/test/{setup,server,multipart}.js
src/index.css                   @import "tailwindcss"; nothing else
```

### Components

- `export default function <Name>Page() {` at the top of the file. Helper components are plain `function` declarations below it in the same file, not exported, until a second screen needs them; then they move to `src/components/<Name>.jsx` as a default export.
- Props destructured in the signature: `function UploadForm({ onUploaded })`.
- Named imports from `react` (`useEffect`, `useState`); no `import React`. No TypeScript, PropTypes, or type comments.

### Naming

- Routed screens `*Page`. Helper components are named for what they render (`UploadForm`, `RejectedRows`, `EmptyRoster`, `CompanyTable`).
- Handlers `handleX` (`handleSubmit`); callback props `onX` (`onUploaded`); API functions verb-first (`listCompanies`, `uploadRoster`).
- State variables are plain nouns or adjectives with a `setX` setter: `companies`, `file`, `uploading`, `loadFailed`, `rejectedRows`. `null` means not loaded yet.
- Module-level lookup constants `SCREAMING_SNAKE` (`STATUS_LABELS`). Component files PascalCase, other modules camelCase.

### Formatting

Prettier's defaults with `semi: false` and `singleQuote: true`, matched by hand since no Prettier config exists:

- 2-space indent. Single quotes in JavaScript, double quotes in JSX attributes. No semicolons. Trailing commas in multi-line literals and argument lists.
- Multi-line JSX wrapped in parentheses after `return`. Wrapped props one per line with the closing `>` on its own line; short elements keep props inline. Self-closing tags for childless elements.
- `.then()` / `.catch()` chains one call per line, indented one level under the call.
- Guard clauses on one line without braces: `if (!file) return`.
- Conditional rendering with `&&`; either/or with a ternary.

### State and data

- `useState` in the page component. State flows down as props; results come back up through `onX` callbacks. No context, reducers, or data libraries.
- **`src/api/<resource>.js` is the only place `fetch` is called.** Functions are `async`, use relative `/api/...` URLs, throw ``new Error(`... failed with HTTP ${response.status}`)`` on unexpected statuses, and return `{ ok: true, ... }` or `{ ok: false, ... }` for rejections the screen must show (`errors` for a rejected upload, `reason` for a refused run, nothing more for a 404). Never `null` as a third shape.
- A fetching effect sets a `cancelled` flag in its cleanup and checks it before setting state.
- Submit handlers are `async function handleSubmit(event)`: `event.preventDefault()`, set the in-flight flag, `try` the API call, `catch` into a failure message, `finally` clear the flag. Disable the submit button while in flight and change its label (`Uploading…`).
- Every screen renders its loading, empty, error, and rejected states explicitly. Error copy tells the user what to check (`Is the Spring server running on port 8080?`).

### Styling

- Tailwind v4 through `@tailwindcss/vite`; no `tailwind.config.js`. Utilities inline in `className`; compose with template literals. No CSS classes of our own, no `clsx`, no CSS modules.
- Palette in use: gray text scale, `bg-blue-600` primary button, `bg-white border-gray-300` secondary button (Cancel), `bg-blue-50 border-blue-200` for the active run's panel, `bg-red-50 text-red-800` for errors, `rounded`, `text-sm`.

### Accessibility, which the tests rely on

- `<label>` wraps its input with a `<span>` caption. Failure messages carry `role="alert"`. Lists the tests locate carry `aria-label`. Non-submit buttons get `type="button"`.

### Tests

- Vitest with `test` and `expect` imported from `vitest`; React Testing Library; MSW handlers added per test with `server.use(http.get(...))`. Multipart bodies are read with `request.text()`; see the comment in `src/test/setup.js` for why.
- Titles are sentences describing behaviour: `a rejected upload lists every bad row with its line and reason and keeps the Roster`.
- Arrange, act, and assert separated by blank lines. Small named helpers (`rosterIs`, `chooseAndUpload`) at the top of the file.
- Query by role and label text (`findByRole`, `getByLabelText`); `findBy*` for anything asynchronous; assert lists as `textContent` arrays. No test ids.
- Screens that use `Link` or `useParams` render inside a `MemoryRouter` (with `Routes` when the screen reads a path parameter). `setup.js` pins `process.env.TZ` to `America/Regina` so formatted times are the same on every machine.

### Tooling

- `npm run dev`, `npm run build`, `npm test` (`vitest run`), `npm run test:watch`. No lint script exists.

---

## 4. Things to avoid

- Tabs; a second indent width, quote style, or semicolon style inside one file.
- `key={index}` in lists. Use a stable field, or a composite of the fields that identify the row.
- Lombok annotations, `@Autowired` field injection, setters on entities, hand-rolled `LocalDate.now()`.
- `jakarta.transaction.Transactional`; the Spring one is the standard.
- `ddl-auto` other than `validate`; editing a migration that has run anywhere.
- `System.out.println` and `printStackTrace`.
- `fetch` outside `src/api/`; absolute `http://localhost:8080` URLs; `VITE_*` host variables.
- `import React`; class components; PropTypes.
- `test`/`should` prefixes on test names; `data-testid`.
- Adding a configuration property without its README row.

---

## 5. Where this repo differs from the Safewalk style

Each row is a deliberate choice to describe the code as it stands. Flip a row here and the rule
above it together, and reformat the affected files in the same change.

| Topic | Safewalk | Here | Why |
|---|---|---|---|
| Frontend indent | 4 spaces | 2 spaces | Every frontend file is already 2-space (Vite scaffold default) |
| JS quotes, semicolons, trailing commas | Double, semicolons, no trailing commas | Single, none, trailing commas | Every frontend file already follows Prettier defaults |
| Java continuation indent | 4 | 8 | Every Java file already wraps google-java-format style |
| Line length | about 120 | about 100 | Matches the Java wrapping already in place |
| Lombok | `@Getter @Setter @Builder`, `@AllArgsConstructor` beans | Not a dependency; hand-written constructors, `final` fields | Records cover DTOs; entities are small |
| Records | Never | DTOs, value objects, config | Java 21 idiom; Spring binds records natively |
| Packages | Layers (`controller`, `service`, `service.impl`, ...) | Feature packages, package-private by default | Keeps a feature's files together for both humans and agents |
| Services | Interface + `*ServiceImpl` | One concrete class | Only one implementation exists |
| Responses | `new ResponseEntity<>(body, HttpStatus.OK)` | Direct body, or `ResponseEntity.ok()` / `badRequest()` | Spring's factory methods |
| Validation | Request bodies taken as-is; failures throw `@ResponseStatus` exceptions | Every row checked, all errors returned in one 400 body | The spec wants the whole file fixed in one pass |
| Table names | `@Table(name = "PascalCase")` | snake_case | The Safewalk guide's own appendix flags PascalCase as a defect |
| Constants | Inline literals; extract at three uses | Named when a test or the schema depends on the value | Messages and column widths are asserted on |
| Javadoc | None | On every type and public method | The domain vocabulary and ADR links live there |
| Config file | `application.properties`, inline `${ENV}` | `application.yml`, `@ConfigurationProperties` records with defaults | Defaults are documented on the record and in the README |
| Logging | `System.out.println` | SLF4J when needed | The Safewalk guide's appendix flags `System.out` as a defect |
| Tests | None | Required, feature-level | See sections 2 and 3 |
| React components | Arrow `const`, `export default` at the bottom, one per file | `export default function`, helpers co-located | Existing screens are written this way |
| Frontend fetching | Inline `fetch(...).then()` with `VITE_SERVER_HOST` | `async` functions in `src/api/`, relative URLs through the Vite proxy | One place to change the contract; no environment variable to misconfigure |
| Loading and error UI | None; failures render an empty list | Explicit loading, empty, error, and rejected states | The screen is the only feedback the user gets |
| Frontend module constants | camelCase | `SCREAMING_SNAKE` | Existing code |

---

## Appendix: Known issues found while writing this

Not style rules. One line each, for follow-up.

- `frontend/`: no ESLint config, so unused imports and hook dependency mistakes go unreported.
