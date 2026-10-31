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
**Separate stat quizzes** add seven individual assessments and a signed-in
character sheet using the latest completed quiz for each stat.

### Separate stat quizzes and character sheet
Open **http://localhost:8080/#/stat-quizzes**. The original 21-question assessment
and standalone practice range remain available and their old results are unchanged.

| Stat | Version 1 content |
|---|---|
| Strength | Eight untimed original fitness self-report questions |
| Perception | Six visual-detail tasks, 75 seconds total, then four untimed questions |
| Endurance | Eight untimed original activity/endurance self-report questions |
| Charisma | Six untimed communication scenarios; optional private photo screen is separate and never scored |
| Intelligence | Ten reasoning/general-knowledge tasks, 180 seconds total, then three reflection questions and two unscored work/education context questions |
| Agility | Twelve nine-cell coordination rounds, 45 seconds total, then four untimed questions |
| Luck | Two chance mini-games: three coin guesses and three die guesses, then four untimed perceived-opportunity questions |

Fitness content is original and informed by
[CDC adult physical activity guidance](https://www.cdc.gov/physical-activity-basics/guidelines/adults.html),
not copied from SportWiki or presented as a validated questionnaire. No physical
exercise, maximum lifting, running or breath-holding is requested. These adult
self-reports cannot measure physiological strength/endurance or prescribe training.
All quizzes remain playful, unvalidated summaries, not medical, personality or
IQ assessments. Fixed tasks repeat on retakes; language, practice, device, disability
and accessibility tools affect results. No IQ, percentile or population norms exist.

Taskless quizzes use scored-question responses only. Other quizzes use **70% task
outcome and 30% reflection**. Each scored option has 0-4 points; reflection
percentage is `round(25 * sum / scoredQuestionCount)`. Task percentage is
`100 * correct / total`, including unanswered tasks in the denominator. The final
game score is `1 + round(9 * weightedPercentage / 100)`. Work/education context
has a prefer-not-to-say option, never adds points, and is not saved. Charisma is
based on chosen communication scenarios, not an inference from appearance.
Luck draws use server-side `SecureRandom` after each choice; coin/die matches have
probabilities 1/2 and 1/6 respectively. Its score deliberately includes chance,
does not predict real-world outcomes and carries no stakes or rewards.

Starting a quiz creates a session-bound attempt. Timed sections have an absolute
**server-enforced deadline**: changing the browser clock, hiding the page or switching
tabs does not pause it. Task answers are recorded sequentially; late answers do
not earn points. Network delay counts, so timing is not reaction time or suitable
for cross-device comparisons. General questions follow without a task timer.
Attempts still expire with the 30-minute idle session. One active stat attempt
is kept per browser session; a new attempt replaces the previous one, including
across tabs. Browser drafts are not restored on reload. Signing into a different
account during an attempt is rejected; a lost session requires starting again.
Retrying the last task choice cannot reroll a chance draw or count twice.
Retrying a successful save in the same attempt returns the same saved result.

Only versioned summaries are persisted: score, reflection percentage, task
correct/attempted/total counts, timestamp and optional ownership. Individual
answers and work/education selections are not persisted. A current task and its
last selection are held temporarily in the server session; the last selection
is cleared on save. Anonymous result links are unlisted; signed-in results are
owner-only and included in **My results**.

Open **http://localhost:8080/#/character-sheet** while signed in. Each stat shows
the latest saved **individual stat quiz**, with timestamp/UUID ordering for ties;
untested stats show **Untested**, not zero or a guessed score. Retakes update
only that stat while preserving older history. Anonymous, legacy combined
assessments and standalone practice results are not auto-claimed or combined.

### Optional private photo and local quality feedback
Open **http://localhost:8080/#/photo** while signed in, or select **Private photo**
in account navigation. The Charisma briefing also links to this separate feature.
Choose your own JPEG/PNG (or one you have permission to store), preview it,
explicitly consent, and select **Save private photo**. Selection alone does not
upload anything. One photo is stored per account in local PostgreSQL, replacing
the previous active photo. It persists in the existing Compose database volume.
There is no public/unlisted photo URL. Saving does not trigger a third-party
request, model download, face detection, identification, or stat-score change.
Optional iLook analysis requires a separate action and consent, described below.

The server decodes/re-encodes uploads into RGB PNG, strips metadata and trailing
content, flattens transparency onto white, and resizes to at most 1200 pixels per
side. Input is limited to **5 MiB**, **6000 pixels per side** and **12 megapixels**,
with a minimum 32 pixels per side. SVG, GIF and other formats are rejected even
if renamed. Image orientation from EXIF is not applied on the server: export a
correctly rotated image from your photo editor if the saved preview is rotated.
Original filenames and original image bytes are not retained.

Feedback uses whole-image luminance, near-black/white pixel proportion, Laplacian
edge variance and resolution. These are uncalibrated technical heuristics, not
face-specific measurements: a plain background can look like low detail, and
lighting/skin tone/background can affect brightness. Results never characterize
the person. No face location, head pose, face count or charisma inference is made.

**Delete stored photo** removes the active database row; replacement/deletion
cannot erase copies in existing PostgreSQL backups, WAL or snapshots. The UI
states this explicitly. Images are not application-level encrypted; secure the
local computer/database and use HTTPS outside local development. Upload/read/
delete operations require the signed-in owner; writes require CSRF and all photo
requests include the expected account to reject cross-tab account changes.
Photo bytes are not intentionally written to application logs or temporary files.
Public deployment still needs upload rate/concurrency limits and a backup/retention
policy; this remains a local prototype, not a public image-hosting service.

### External face API research (2026-10-31)
iLook is integrated but **disabled by default** until an operator-issued API key
is configured. A private upload is **not** consent to external analysis.

| Candidate | Verified capability | Free-use/privacy conclusion |
|---|---|---|
| [iLook](https://www.ilook.fit/api/docs) | Versioned subjective appearance scores, explanations, symmetry and focused tools including PSL-style feedback | [Auth docs](https://www.ilook.fit/auth.md) specify 100 requests per issued key per UTC day; keys are manually provisioned. [Pricing](https://www.ilook.fit/pricing.md) lists API access as free within key limits, separately from paid subscriptions. API images are described as transient, but downstream providers, processing regions, training use and precise deletion timing remain unclear. |
| [Face++](https://www.faceplusplus.com/beauty/) | Advertises a beauty attribute alongside face quality/landmarks | Could not verify a current free quota or whether beauty is included from accessible documentation. Its [privacy policy](https://www.faceplusplus.com/privacy-policy/) describes deletion/anonymization "as soon as possible", not a fixed TTL, China/Canada processing, and network logs retained at least six months. Not enabled or endorsed. |
| [Luxand.Cloud](https://www.luxand.cloud/pricing) | Offers face APIs | Pricing page metadata advertises a 14-day trial, not an ongoing free tier. A suitable beauty-rating API/terms were not verified. |
| [AWS Rekognition](https://docs.aws.amazon.com/rekognition/latest/APIReference/API_DetectFaces.html) | Face brightness/sharpness, bounding box and pose | No beauty rating. [Usage pricing](https://aws.amazon.com/rekognition/pricing/) applies; introductory credits are not permanent free access. Review the [AI-services opt-out policy](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out.html) before any images are sent. |
| [MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/vision/face_detector/web_js) | Local face boxes and landmarks | A possible future self-hosted alternative to cloud calls, not a face attractiveness rating. Not installed in this milestone; review model/code licenses before shipping. |

iLook was selected for an optional integration, not as an endorsement of
"blackpill" claims. Appearance scores are subjective and model-dependent, not scientific measurements
of attractiveness, genetics, character or life prospects. Do not mix them into
Perception/Charisma or present them as objective conclusions about a person.

### Optional iLook feedback for Charisma
Obtain a beta key directly from the iLook operator; there is no public key signup.
Before enabling it for other users, clarify multi-user application permission and
the unresolved data-processing details in its [privacy policy](https://www.ilook.fit/privacy)
and [terms](https://www.ilook.fit/terms). No live provider analysis has been verified
without a key; automated checks use a local mock provider, never personal photos.

Set `ILOOK_API_KEY` **only on the server**, then restart the Java application or
recreate the Compose app service. For PowerShell, read it without putting its
value in command history:

```powershell
$credential = Get-Credential -UserName 'ilook' -Message 'Enter the iLook API key in the password field'
$env:ILOOK_API_KEY = $credential.GetNetworkCredential().Password
try {
    # DB_PASSWORD must already be set to the existing database password.
    docker compose up -d --no-deps app
} finally {
    Remove-Item Env:ILOOK_API_KEY
    Remove-Variable credential
}
```

Do not commit keys, paste them into chat, pass them as command-line arguments,
or put them in browser code/storage. Compose passes the variable to the server
container; users with Docker/host administrator access can inspect its environment.
To disable the integration, unset the variable and recreate the app service.

From the Charisma briefing or **Private photo**, save a photo, confirm the
pictured person is **18+** and you have authorization, separately consent to
iLook processing, then select **Analyze saved photo with iLook**. Neither saving,
loading the screen nor taking the quiz sends images externally. The server sends
only that exact saved revision's normalized PNG as multipart data to the fixed
`https://www.ilook.fit/api/v1/score` endpoint, with `adult_and_authorized=true`.
No username, original filename, session cookie or questionnaire answers are sent.
The provider's decimal **5 MB** image limit is enforced separately from local
storage's 5 MiB limit.

The report displays an overall subjective appearance score, five subscores with
explanations, summary, confidence and model/rubric/analysis identifiers. It is
kept only in the page for this visit, not stored in PostgreSQL/history or included
in the Charisma calculation. Replacing/deleting the photo or navigating away
clears the displayed report. Provider text is rendered as text, not HTML.
Requests have 3-second connect/45-second read timeouts (the browser allows
60 seconds for this action), bounded response size and at most two
concurrent provider calls per server. Quota exhaustion, photo rejection,
configuration errors and malformed reports are surfaced without exposing provider
response bodies. There are no automatic retries; a failed/aborted request may
still consume provider quota. Distributed/per-user abuse controls and a complete
data-processing review remain prerequisites for public deployment.

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
| `ILOOK_API_KEY` | Empty/disabled by default. Server-only, operator-issued iLook key for optional adult photo feedback; never changes stat scores. |

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

The image builds and runs with Java 21; the runtime uses an unprivileged `app`
user. Its build stage runs unit tests; run
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
| `GET /api/v1/me/results?page=0&size=20` | Authenticated only; mixed newest-first history `{items, page, size, hasNext}`. Size must be 1-50; page non-negative. Each item has `id`, `kind` (`SPECIAL`/`PRACTICE`/`STAT`), `title`, `version`, `createdAt`, and optional task `correct`/`total`. |

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

### Individual stat quiz API
All writes use the existing CSRF/session flow. Definitions are immutable and
seeded by V7 after the additive V6 schema; publish future changes as new versions
in new migrations rather than editing applied content.

| Method and path | Contract |
|---|---|
| `GET /api/v1/stat-quizzes` | Seven ordered quiz details, untimed questions/options and timing/scoring notices; no answer keys |
| `GET /api/v1/stat-quizzes/{stat}/versions/{version}` | Versioned briefing and untimed questions; stat IDs are lowercase attribute names |
| `POST /api/v1/stat-quizzes/{stat}/versions/{version}/attempts` | No body. Start/replace the current session attempt; supports `X-Result-Privacy` and `X-Result-Account` expectations |
| `GET /api/v1/stat-quiz-attempts/{uuid}` | Current task state, `serverNow`, absolute `deadline` (or null), answered/total, optional chance feedback |
| `POST /api/v1/stat-quiz-attempts/{uuid}/answers` | JSON `{questionId, optionId}` for the current task; returns next state. Only grid tasks expose their target |
| `POST /api/v1/stat-quiz-attempts/{uuid}/result` | JSON `{answers: [{questionId, optionId}]}` for all untimed questions; saves summary only after task completion/expiry; `201` and result location |
| `GET /api/v1/stat-quiz-results/{uuid}` | Immutable summary with `privateResult`; private reads require the owner |
| `GET /api/v1/me/character-sheet` | Authenticated only; seven entries with latest `resultId`, `score`, `createdAt` or null for untested stats, and current `quizVersion` for retakes |

Malformed answers return `400`; out-of-order task choices, changed anonymous
sign-in state and premature saves return `409`. A replaced/expired attempt
returns `404`; an owned attempt whose account changes returns `401`. Ownerless
and account-owned attempts cannot change ownership on save. The deadline is
checked when a task request reaches the server, not from a submitted duration.

### Private photo API
All routes require authentication and the `X-Result-Account` username header;
account mismatches return `409`. Authentication is mandatory before a photo is
read or changed. Reads use no-store caching and images are served as PNG.

| Method and path | Contract |
|---|---|
| `GET /api/v1/me/photo` | `{stored, revision, updatedAt, analysis}`; analysis includes dimensions, whole-image brightness/detail and feedback, or null when absent |
| `GET /api/v1/me/photo/image` | Owner's PNG bytes or `404`. Send `Accept: image/png`; no public UUID/filename lookup |
| `POST /api/v1/me/photo` | Raw JPEG/PNG body, matching Content-Type, CSRF, `X-Photo-Consent: true`, expected-account header; validate/normalize and upsert the owner's photo |
| `DELETE /api/v1/me/photo` | CSRF and expected-account header; idempotently remove owner's active photo, `204` |
| `GET /api/v1/me/photo/ilook` | Owner-only `{available}`; no provider call or key disclosure |
| `POST /api/v1/me/photo/ilook` | JSON `{revision, externalConsent: true, adultAndAuthorized: true}`, CSRF and expected-account header; analyze only the owner's matching saved revision and return an ephemeral report |

Malformed/unsupported decoded images return `400`; unsupported HTTP content types
return `415`; uploads over 5 MiB return `413`. A failed validation does not replace
the old photo. V8 adds this private table without changing existing result data.

### UML
[Use case diagram](readme_res/stats_use_case.png)

### VCS
* [Main branch](https://github.com/VsevolodSvet/stats-calculator/tree/master)
* [Develop branch (unstable)](https://github.com/VsevolodSvet/stats-calculator/tree/develop)