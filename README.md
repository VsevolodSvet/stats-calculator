# Stats calculator

### Description
The following web service provides an opportunity to calculate
and to share your own stats transferred into S.P.E.C.I.A.L. RPG system, 
using several different tests.<br>
* [About S.P.E.C.I.A.L.](http://specialrpg.wikidot.com)

### Web application roadmap
[Java 21 web MVP plan](ROADMAP.md): current project findings, recommended tools,
API and storage design, visual direction, and phased implementation checklist.

### Current implementation
Java 21, Spring Boot 4.1.2, Maven 3.9.11, and PostgreSQL 17.
The API serves one versioned 21-question S.P.E.C.I.A.L. self-assessment,
calculates seven scores from 1 to 10, and saves results with unguessable UUIDs.
Flyway creates the schema and seeds the questions. Raw answers are not stored.
Open **http://localhost:8080** for the Fallout-inspired browser application.
It is served by Spring Boot using HTML, original CSS visuals, and vanilla
JavaScript; no Node.js installation or frontend build is required.
The **Practice range** also includes visual-detail matching, reasoning puzzles,
and target-selection coordination with separate saved summaries.
Optional username/password accounts now provide a **My results** screen for
private assessment and practice history.

### Browser experience
Choose an assessment, read its instructions, and answer each question. Previous
and Next preserve responses; the progress indicator shows answered questions.
Review or revise responses, acknowledge the sharing notice, and select
**Calculate & save record** to see all seven scores and their explanations.
For anonymous results, copy the unlisted link to reopen the same record.
Signed-in results are private and reopen from **My results**, without a public
copy/share action. Retaking creates a separate record.

Result URLs use `/#/results/{uuid}` and work on reload; private results require
the owner's signed-in session. The interface supports keyboard navigation, visible focus, native
radio/checkbox validation, narrow screens, and reduced-motion preferences.
Loading, empty, missing-record, and connection-error states include recovery
actions. A failed submission preserves answers and the sharing acknowledgement
so it can be retried. Automatic clipboard failures select the link for manual
copying rather than falsely reporting success.

Unsaved answers stay only in the current page's memory: a reload, closing the
page, or leaving the assessment clears them. There is a confirmation for
in-app links that leave an answered assessment and a browser unload warning
(subject to browser support). Browser Back/Forward can also leave the assessment;
there is no persistent draft storage. Keep the page open while saving.

**Limitations and privacy:** this is playful self-reflection, not a validated
assessment of health, fitness, IQ, or future success. Luck reflects perceived
opportunity. Accounts are optional. Anonymous results and pre-account results
remain visible to anyone with their unlisted links; signed-in results are private
to the saving account. There is no public results list. Retention and
deletion controls are not implemented, so this is a local MVP, not a public launch.

### Accounts and personal results
Use **Create account** in the terminal navigation, then sign in. Usernames are
case-insensitive, normalized to lowercase, and accept 3-30 ASCII letters, digits,
or underscores. Passwords must be 12-128 characters; they are stored only as
salted PBKDF2-HMAC-SHA256 hashes (Spring Security's v5.8 defaults: 310000 iterations,
16-byte salt, 256-bit output), never plaintext.

Open **http://localhost:8080/#/my-results** to see a newest-first, paginated history
of both S.P.E.C.I.A.L. and practice records. New signed-in submissions belong to
that account and are readable only by its owner. Another account or an anonymous
visitor gets `404` for their IDs, even if they know a result URL.

Existing and newly anonymous results stay unlisted; registration does not claim
old results. They do not appear in personal history. There is no private-result
publishing, deletion, account recovery, email verification, password change, or
account-deletion flow in this milestone. Do not lose the password for important
records; this is still a local prototype.

Authentication uses an HttpOnly, SameSite=Lax session cookie, with session ID
rotation at sign-in and invalidation on sign-out. Sessions expire after 30 minutes
of inactivity and are not persisted across application restarts. Accounts and
saved results persist in PostgreSQL. There are no tokens in browser storage.
The browser fetches a fresh CSRF token before every write, including anonymous
submissions, registration, login, and logout. If a private draft's session expires,
the save is rejected rather than silently publishing it as anonymous. Sign back
in in another tab to retry the intact draft. Changing accounts during a draft is
also rejected instead of saving it under a different account.

For HTTPS deployment set `SESSION_COOKIE_SECURE=true`; leave it false only for
local HTTP. Public deployment still requires HTTPS, login/registration abuse
limits, recovery/retention decisions, operational monitoring and backups.

### Practice range
Open **http://localhost:8080/#/practice** or select **Explore practice activities**
on the landing page.

| Activity | Version 1 format | Recorded outcome |
|---|---|---|
| Visual-detail matching | Six untimed signal-code matches | Correct count and accuracy percentage |
| Reasoning puzzles | Six untimed original logic/pattern questions | Correct count and accuracy percentage |
| Target-selection coordination | Twelve rounds on a fixed nine-cell grid; each selection advances, including misses | Correct count, accuracy, mode, input method, and optional elapsed time |

These are **separate practice results**, not additional S.P.E.C.I.A.L. scores.
No averaging, attribute conversion, percentile, IQ estimate, or ranking is
performed. Fixed content means repeated attempts involve learning. Fonts,
language, screen size, input device, accessibility tools, and familiarity affect
performance, so results should not be used to compare people or devices.

Coordination defaults to **Untimed**, supports mouse/touch and keyboard, and has
no moving targets. **Elapsed-time** mode has no deadline; it records total time
from starting the activity until the last selection, including navigation and
rendering. It is not reaction time. Losing window focus or hiding the tab
interrupts timing: continue untimed with completed selections retained, or
restart from the briefing. Timing and input-method classification are
browser-reported, not verifiable performance measurements.

Review the summary and acknowledge sharing before saving. Only correct/total,
versions, mode, input method, optional duration, timestamp, and optional account
ownership are stored.
Individual selections are discarded. Unlisted URLs use
`/#/practice/results/{uuid}`; anonymous records are visible to anyone with the
link, while private records require the saving account.
Failed saves keep the draft available for retry; retakes create new records.

### Local development (PowerShell)
Install JDK 21 and Docker with Linux containers enabled. The Maven Wrapper
downloads Maven automatically. Initial builds and container pulls need internet
access. Run commands from the repository root.

```powershell
# Replace this example with your JDK 21 installation directory.
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -version

# Choose a local password; keep it consistent when reusing the database volume.
$env:DB_PASSWORD = 'replace-with-your-local-password'
docker compose up -d --wait db
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Both version commands should report Java 21. The wrapper uses `JAVA_HOME`, which
can differ from the `java` executable on PATH. The application listens on port
8080 by default; stop it with Ctrl+C. A database connection or migration failure
prevents startup rather than silently switching to in-memory storage.

To run the packaged application instead:

```powershell
java -jar .\target\stats-calculator-0.0.1-SNAPSHOT.jar
```

| Variable | Default / purpose |
|---|---|
| `DB_PASSWORD` | Required by both Compose and the application; do not commit real passwords. |
| `DB_USERNAME` | `stats_calculator` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/stats_calculator` |
| `DB_PORT` | Compose host DB port, default `5432`; if changed for local Java execution, also change `DB_URL`. |
| `SERVER_PORT` | Spring Boot HTTP port, default `8080`. |
| `SESSION_COOKIE_SECURE` | `false` for local HTTP; must be `true` when deployed with HTTPS. |

Compose binds host ports to loopback. For example, if port 5432 is occupied, set
`DB_PORT` to `55432` and `DB_URL` to
`jdbc:postgresql://localhost:55432/stats_calculator` before running locally.
Compose's application service always connects to the internal `db:5432`.
Do not pass real passwords as command-line arguments or commit them to source.

### Tests
```powershell
# Pure Java scoring tests; no database or Docker needed.
.\mvnw.cmd test

# Scoring plus HTTP, Flyway, JPA, and PostgreSQL integration tests.
.\mvnw.cmd verify
```

Integration tests run through Maven Failsafe (`*IT`) and start an isolated
PostgreSQL container using Testcontainers. They need a running Docker daemon,
not the Compose development database or `DB_PASSWORD`. `package` runs unit tests;
`verify` runs both suites. Integration tests cover malformed submissions,
missing resources, score bounds, database constraints, and failed result writes.

For optional end-to-end browser checks, install Python 3.10+ and Playwright's
Chromium, then run against a running application with a disposable/test database:

```powershell
python -m pip install -r .\tests\browser\requirements.txt
python -m playwright install chromium
$env:TEST_BASE_URL = 'http://localhost:8080'
python -m unittest discover -s .\tests\browser -p 'test_*.py' -v
```

These tests submit real assessments and leave their results in the target
database; do not point them at production. They cover keyboard-only completion,
mobile layout, revising answers, consent, simulated loading/submission errors,
retry, reopening results without a session, clipboard success/fallback, and
retakes. They run separately from Maven. Set `TEST_SCREENSHOT_DIR` to an absolute
directory outside the repository to save desktop/mobile screenshots.
The suite also covers all three practice activities, keyboard target selection,
untimed/timed modes, timing interruption and recovery, practice save retries,
separate result links, and retakes. Practice tests also leave real summary
records in their target database.
Account browser checks create test accounts and verify registration/login/logout,
private history for both result types, owner isolation, and expired-session retry.

### Docker
To run both the application and database without a local JDK:

```powershell
$env:DB_PASSWORD = 'replace-with-your-local-password'
docker compose up --build -d
docker compose logs -f app
```

The image builds and runs with Java 21. Its build stage runs unit tests; run
`.\mvnw.cmd verify` on the host for Docker-backed integration tests (the Docker
build does not require access to a nested Docker daemon). Wait for the
`Started StatsCalculatorApplication` log before making requests. Stop following
logs with Ctrl+C; stop the stack with `docker compose down`.

The named `postgres-data` volume preserves definitions and results across
application restarts, `docker compose down`, and database-container recreation.
Changing `DB_PASSWORD` does not change the password of an already initialized
database: reuse its original password or rotate it inside PostgreSQL.
Only to deliberately erase **all local database data**, use
`docker compose down --volumes`; the next startup recreates and reseeds it.

### API
| Method and path | Response |
|---|---|
| `GET /api/v1/assessments` | Published assessment summaries, versions, question counts, and duration. |
| `GET /api/v1/assessments/{id}/versions/{version}` | Instructions and ordered questions/options, without scoring weights or mappings. |
| `POST /api/v1/results` | JSON-only submission; `201 Created`, saved result, and `Location` header. |
| `GET /api/v1/results/{uuid}` | Saved scores, explanations, versions, timestamp, and disclaimer. |

The initial assessment ID is `special-self-assessment`, version `1`.
To complete an example submission from PowerShell:

```powershell
$base = 'http://localhost:8080/api/v1'
$assessment = Invoke-RestMethod "$base/assessments/special-self-assessment/versions/1"
$assessment.instructions

# For demonstration only: choose the middle option for every question.
$answers = @($assessment.questions | ForEach-Object {
    @{ questionId = $_.id; optionId = $_.options[2].id }
})
$body = @{
    assessmentId = $assessment.id
    assessmentVersion = $assessment.version
    answers = $answers
} | ConvertTo-Json -Depth 10

$csrf = Invoke-RestMethod "$base/auth/csrf" -SessionVariable terminalSession
$headers = @{ 'X-Result-Privacy' = 'UNLISTED' }
$headers[$csrf.headerName] = $csrf.token
$result = Invoke-RestMethod "$base/results" -Method Post -WebSession $terminalSession `
    -Headers $headers -ContentType 'application/json' -Body $body
$result.scores
Invoke-RestMethod "$base/results/$($result.id)" -WebSession $terminalSession
```

Every question must be answered exactly once using one of its own option IDs.
Incomplete sets, duplicates, unknown questions/options/versions, unknown JSON
fields, and client-supplied scores are rejected with `400`. Missing reads return
`404`, malformed UUIDs return `400`, and non-JSON submissions return `415`.
Errors use `application/problem+json`. Storage failures are logged and return
`503`, never a successful unsaved result. Each successful submission creates a
new result; after a lost response, retrying may create a second result.

Version 1 uses three equally weighted 1-5 answers per attribute and calculates
`1 + round(9 * (raw - n) / (4.0 * n))` on the server. All minimum answers produce
1; all maximum answers produce 10. Scores have no shared points budget.
Published definitions are read-only in the application. Publish changes as a new
version with new IDs in a new Flyway migration; never edit applied migrations or
overwrite published definitions.

### Practice API
| Method and path | Response |
|---|---|
| `GET /api/v1/practice/activities` | Published practice summaries, item counts, and whether coordination modes are available. |
| `GET /api/v1/practice/activities/{id}/versions/{version}` | Instructions and ordered items/options. Choice answer keys are omitted; coordination exposes the target to render. |
| `POST /api/v1/practice/results` | Validate selections, calculate accuracy on the server, save summary, and return `201` with `Location`. |
| `GET /api/v1/practice/results/{uuid}` | Saved summary, versions, timestamp, mode, input method, optional elapsed time, and caveats. No selections or result listing. |

Version 1 activity IDs are `visual-matching`, `reasoning`, and `coordination`.
Submit JSON shaped as:

```json
{
  "activityId": "reasoning",
  "activityVersion": 1,
  "mode": "UNTIMED",
  "inputMethod": "KEYBOARD",
  "elapsedMs": null,
  "selections": [
    { "itemId": "reason-1", "optionId": "b" }
  ]
}
```

The abbreviated example above must be expanded to include every item exactly
once before submission. `inputMethod` is `POINTER` (mouse/touch), `KEYBOARD`, or
`MIXED`. Choice activities only accept `UNTIMED`; untimed submissions must omit
`elapsedMs` or send null. Only coordination accepts `TIMED`, with integer
`elapsedMs` between 1 and 86400000 inclusive. Incomplete/duplicate selections,
foreign items, invalid options/versions/modes, invalid timing, and client-supplied
score fields return `400`; missing reads return `404`. Non-JSON submissions
return `415`, and database failures return `503`.

The summary contains `correct`, `total`, and
`accuracyPercent = round(100.0 * correct / total)`; time never changes accuracy.
Practice definitions are immutable versioned JSON documents in PostgreSQL,
seeded by Flyway; answer keys never come from the submission. Publish future
content through new version IDs and new migrations, not edits to applied ones.
Migration V3 adds practice tables without changing existing assessment/results.
V4 also enforces that timed results cannot have a null duration at the database
level (in addition to API validation).

### Authentication and history API
| Method and path | Contract |
|---|---|
| `GET /api/v1/auth/csrf` | Start/reuse a session and return `{headerName, token}`. Send this token in the named header on all subsequent writes. Fetch again after login/logout. |
| `GET /api/v1/auth/me` | `{authenticated, username}`; anonymous requests return `authenticated: false`. |
| `POST /api/v1/auth/register` | JSON `{username, password}`; `201` on success, `400` for invalid fields, `409` for unavailable username. Does not sign in automatically. |
| `POST /api/v1/auth/login` | Form-encoded `username` and `password`, plus CSRF header; `204` on success or generic `401` on failure. Keep the session cookie. |
| `POST /api/v1/auth/logout` | CSRF-protected session invalidation; `204`. |
| `GET /api/v1/me/results?page=0&size=20` | Authenticated only; mixed newest-first history `{items, page, size, hasNext}`. Size must be 1-50; page non-negative. Each item has `id`, `kind` (`SPECIAL`/`PRACTICE`), `title`, `version`, `createdAt`, and optional practice `correct`/`total`. |

Result responses now include `privateResult` and a matching privacy notice.
Ownership always comes from the authenticated session, never a body field.
Browser submissions send `X-Result-Privacy: PRIVATE` or `UNLISTED` and
`X-Result-Account` (the signed-in username or empty) to guard against expired
sessions or account changes in another tab. A private expectation without a
session returns `401`; an incompatible current account/privacy state returns
`409`. API clients should also send these expectations when privacy matters.
Without them, ownership follows the current session.

Anonymous visitors can still browse and submit results, but all POSTs now need a
session-bound CSRF token. Missing/invalid CSRF returns `403` with a problem
response. Personal history without login returns `401`; private IDs for anyone
other than their owner return `404`. Basic authentication is disabled. Sensitive
responses use no-store caching headers, and no cross-origin permission is
configured. The privacy change applies to new signed-in saves only: Flyway V5
adds nullable owner references without rewriting old results or their links.

### UML
[Use case diagram](readme_res/stats_use_case.png)

### VCS
* [Main branch](https://github.com/VsevolodSvet/stats-calculator/tree/master)
* [Develop branch (unstable)](https://github.com/VsevolodSvet/stats-calculator/tree/develop)