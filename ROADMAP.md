# S.P.E.C.I.A.L. web application roadmap

Status: checkpoints A-D complete for the local MVP. Java 21, PostgreSQL
persistence, the seeded assessment, scoring, JSON API, and browser experience
are implemented. The first follow-up milestone adds three practice activities
with separate results; public deployment remains future work. The project
findings below describe the pre-migration baseline. Optional accounts and a
private personal-results screen are now implemented as well (section 11).

## 1. Goal

Build a Java 21 web service where a visitor completes assessments and receives a
Fallout-inspired character sheet for Strength, Perception, Endurance, Charisma,
Intelligence, Agility, and Luck. Store assessment definitions and results, expose
them through a JSON API, and make the browser the first client. Keep the API
usable by a possible future mobile application.

These are playful self-reflection scores, not scientifically validated measures
of ability, health, intelligence, or future success. In particular, Luck cannot
be objectively established by a questionnaire. Explain this before testing and
on the result screen; never present results as a diagnosis.

## 2. What already exists

| Project / file | Current state | Implication |
|---|---|---|
| `stats-calculator/pom.xml` | Spring Boot 2.4.1, Java 11 target, Kotlin 1.4.21, Maven | Upgrade the framework and build for Java 21; do not just change the language target. |
| `StatsCalculatorApplication.kt` | Application entry point, Basic authentication with demo users, `/main` greeting | Replace the skeleton with readable Java and explicit API/security configuration. No assessment business logic needs porting. |
| `StatsCalculatorApplicationKtTest.kt` | Empty JUnit 4 test body | Replace with meaningful JUnit Jupiter tests. |
| `application.properties` | Empty | Database and runtime configuration still need to be introduced. |
| `Dockerfile` | Java 11 build and runtime images | Move both stages to Java 21 and align the container build with Maven. |
| `readme_res/stats_use_case.png` | Accounts, administration, calculation, storage, sharing | Preserve the long-term direction, but defer accounts and administration from the first vertical slice. |
| Neighbouring `FalloutCalculator` | Java Android application using Android SDK 27-era dependencies | Reference for appearance and interactions, not a web frontend that can be reused directly. Leave this project unchanged. |
| Android layouts and drawables | Rusted metal background, amber monospace text, dark green panels, circular red buttons and pressed states | Translate the visual language into responsive HTML/CSS. |
| Android activities | Login/register and user-list UI scaffolding; no implemented assessment or server integration in the inspected activities | Design the web assessment flow rather than attempt to migrate missing functionality. |

## 3. Recommended tools and architecture

These are proposed defaults for the next implementation step, not installed tools
or final decisions. Confirm material product/stack changes before implementing.

| Area | Recommendation | Reason |
|---|---|---|
| Runtime | Java 21 LTS; a maintained Spring Boot release compatible with Java 21 | Matches the requested language and preserves the existing Spring foundation. Select and pin the supported framework version when implementation starts. |
| Build | Maven and the Maven Wrapper | Already present; upgrade the wrapper if required by the selected framework. |
| API | Spring MVC REST controllers, Bean Validation, explicit request/response DTOs | Readable Java, validated inputs, and a reusable API without exposing persistence entities. |
| Storage | PostgreSQL, Spring Data JPA, Flyway SQL migrations | Durable storage and reproducible schema/content changes. Use PostgreSQL from the start rather than a different database only for development. |
| Frontend | HTML, CSS, and small vanilla JavaScript modules served by Spring Boot | One application and origin, no Node build or separate frontend deployment for the MVP. Fetch all assessment data from the JSON API. |
| Local database | Docker Compose with a named PostgreSQL volume | Repeatable local startup and persistence across container recreation. |
| Testing | JUnit Jupiter, MockMvc, PostgreSQL integration tests with Testcontainers | Cover scoring, HTTP behavior, migrations, and the actual database engine. Docker is needed for container-based tests. |
| API exploration | IntelliJ HTTP Client, curl, or Bruno | Exercise the API independently of the browser; choose whichever is already convenient. |
| Editor | IntelliJ IDEA with JDK 21 | Familiar Java navigation/debugging; no Lombok needed for this small application. |
| Browser tooling | Browser developer tools; Playwright when browser automation is added | Inspect responsive layouts and network failures; automate the core journey once it exists. |

Keep a single deployable application, not microservices. Use straightforward
feature packages such as `assessment` and `result`, with controllers, services,
repositories, DTOs, and entities where needed. Use Java records for immutable
DTOs and ordinary classes for JPA entities. Keep scoring as a pure Java component
that does not depend on HTTP or the database.

Do not add React, Redis, Kafka, Kubernetes, OAuth, or a generic assessment-engine
framework to the MVP. A richer frontend can replace the browser client later
without changing the core service contract.

## 4. Today's proposed MVP

Target a complete local vertical slice, not a login screen or a static mockup:

1. Open a retro-styled landing page and see what the assessment measures.
2. Choose the initial S.P.E.C.I.A.L. self-assessment.
3. Answer 21 original questions: three per attribute, five choices per question.
4. See progress, move backward without losing answers, and submit a complete set.
5. Receive seven integer scores from 1 to 10, with short, non-judgmental explanations.
6. Save the result in PostgreSQL and reopen its unlisted link after an application restart.
7. Retake the assessment and create a separate result without overwriting the old one.

The initial release contains one complete questionnaire covering all seven
attributes. Multiple distinct test types are the next milestone, not a claim
that the MVP already measures physical performance or cognitive ability.

**Not in today's MVP:** registration, passwords, user search, public result lists,
admin UI, leaderboards, multiplayer, scientifically normed tests, mobile builds,
or public production deployment.

**Confirmed privacy default:** no name, email, or account is required. Anyone
with an unlisted result URL can view its scores; it is not private authenticated
storage. Do not expose raw answers through result reads or enumerate results.
Show the link-sharing implications before submission. Decide retention and
deletion before opening the service to the public.

## 5. Assessment and scoring contract

- Store the published assessment version, question IDs, question order, attribute
  mapping, answer options, and scoring version. Seed the first version through a
  migration; no editor is needed yet.
- Start with equally weighted, positively keyed questions and answer values 1-5.
  Write original prompts and explain that self-reporting is subjective.
- For an attribute with `n` answers and raw sum `raw`, calculate
  `1 + round(9 * (raw - n) / (4.0 * n))`, using Java's `Math.round`.
  With three answers, all 1s must yield 1 and all 5s must yield 10.
- Calculate only on the server. Clients submit question IDs and option IDs,
  never trusted score values. Keep scoring details out of the public question DTO.
- Reject unknown/duplicate questions, invalid options, unsupported versions, and
  missing answers. Do not quietly accept a partial result or clamp invalid input.
- Save the assessment/scoring versions and all seven calculated scores. Published
  versions are immutable so future question changes cannot alter old results.
- Keep each attribute independent. Do not force a shared Fallout character-creation
  points budget: these scores describe responses, not allocation choices.
- Frame Luck as perceived opportunity/chance in the initial self-assessment;
  neither randomized output nor a claim to predict real-world luck.

## 6. Initial API and storage outline

Proposed versioned JSON endpoints:

| Endpoint | Responsibility |
|---|---|
| `GET /api/v1/assessments` | List published assessment summaries and estimated duration. |
| `GET /api/v1/assessments/{id}/versions/{version}` | Return instructions, ordered questions, and selectable options for an immutable version. |
| `POST /api/v1/results` | Validate a versioned answer set, calculate scores, persist a result, and return `201 Created` with its location. |
| `GET /api/v1/results/{id}` | Return saved scores, explanations, versions, and creation time; return `404` for an unknown result. |

Submission shape: `{ assessmentId, assessmentVersion, answers: [{ questionId,
optionId }] }`. Result shape: `{ id, assessmentId, assessmentVersion,
scoringVersion, createdAt, scores: { strength, perception, endurance, charisma,
intelligence, agility, luck } }`, plus attribute explanations.

Use unguessable result identifiers. Use consistent problem responses for invalid
requests and errors; distinguish validation failures from missing resources.
Never return a successful result if the database write failed.

Start with tables for assessment versions, questions, answer options, and results.
Store the seven scores explicitly with 1-10 database constraints and a foreign
key to the immutable assessment version. Do not persist individual submitted
answers in the MVP unless a concrete feature needs them.

Serve frontend and API on the same origin; no permissive cross-origin setup is
necessary. Replace the old demo authentication rather than carry its credentials
forward. Make anonymous access rules explicit and review CSRF handling against
the chosen non-cookie API design; revisit protection when authentication arrives.

## 7. Visual direction

- Build a Fallout 1/2-inspired industrial console: weathered metal frame,
  recessed near-black/green displays, amber text, and tactile red controls.
- Use the neighbouring app's `login_activity.xml`, `list_activity.xml`,
  `common_button.png`, and `rusted_metal_texture.jpg` as visual references.
  Reuse actual files only after confirming ownership/licensing; otherwise create
  original CSS textures and controls. Do not copy game artwork, logos, or fonts.
- Prioritize the assessment and character sheet over reproducing the old
  login/register screen. Give all seven attributes a readable labeled meter.
- Provide loading, empty, validation, network-error, and saving states.
  Preserve entered answers on failed submission and allow retry.
- Support keyboard navigation, visible focus, labeled inputs, adequate contrast,
  and narrow screens. Do not rely on color alone or sacrifice legibility for
  distressed effects. Any CRT effects must be subtle and respect reduced motion.

## 8. Implementation checklist and order

Each checkpoint should leave a runnable application. The time boxes are rough
planning estimates, not a delivery guarantee; dependency/tool availability and
content review can change the schedule.

### A. Java foundation (roughly 45-90 minutes)

- [x] Confirm the proposed stack, anonymous-result behavior, and questionnaire scope.
- [x] Select a maintained Java-21-compatible Spring Boot version (4.1.2).
- [x] Replace Kotlin application/tests with Java under standard Maven directories.
- [x] Remove Kotlin plugins/dependencies and unused Lombok/JUnit 4 setup.
- [x] Replace obsolete security configuration and remove demo users/greeting.
- [x] Align Maven compiler, Maven 3.9.11 wrapper, and both Docker stages with Java 21.
- [x] Add an application-startup test and document the local prerequisites.

Done when the Java-only application starts on JDK 21 and its test suite runs.

### B. First assessment and persistence (roughly 90-150 minutes; depends on A)

- [x] Add PostgreSQL Compose configuration, named volume, and environment configuration.
- [x] Add Flyway schema and seed one immutable assessment with 21 original questions.
- [x] Implement and test deterministic scoring independently of Spring.
- [x] Implement validated DTOs, assessment reads, submission, and saved-result reads.
- [x] Add structured error responses and persist results transactionally.
- [x] Test migrations and repositories against PostgreSQL.

Done when an API client can fetch questions, submit valid answers, and retrieve
the same saved result after restarting the application without resetting the DB.

Verified with PostgreSQL-backed API tests and a containerized smoke check that
saved scores and timestamps survive application restart and database-container
recreation with the same named volume. The smoke-check containers and their
isolated test volume were removed afterward; development storage was not reset.

### C. Browser experience (roughly 90-150 minutes; depends on B)

- [x] Build the industrial-console shell with original/licensed visuals.
- [x] Add the landing page and assessment selection backed by the API.
- [x] Add question navigation, answer preservation, progress, and completeness checks.
- [x] Submit to the API and render seven scores with explanations.
- [x] Add a reopenable result URL, copy-link action, and retake action.
- [x] Add accessible focus/input states and explicit loading/error/retry behavior.

Done when a visitor can complete the entire flow without API tooling.

Implemented with static HTML/CSS and JavaScript modules served by Spring Boot,
with no production frontend dependencies or copied artwork. Hash-based result
URLs reopen directly. Draft answers are kept only in page memory, not browser
storage or the database. Reloading or leaving the assessment clears them.

### D. MVP acceptance and handoff (roughly 45-90 minutes; depends on C)

- [x] Verify scoring bounds, rounding boundaries, all-minimum/all-maximum answers,
  and independent attribute mapping.
- [x] Verify rejection of incomplete, duplicate, foreign-version, and invalid answers.
- [x] Verify `201`, `400`, and `404` contracts and non-success on persistence failure.
- [x] Verify saved results survive application restart and DB container recreation
  with the same named volume.
- [x] Walk the complete browser journey, keyboard-only navigation, a narrow viewport,
  and a failed submission followed by retry without losing answers.
- [x] Update README with exact startup/test commands, configuration, API examples,
  data persistence/reset behavior, and the assessment disclaimer.

Done when the local demo meets every item in section 4. If time gets tight,
reduce decorative effects first; do not replace storage, validation, or working
scoring with mocks. This schedule targets a focused day's local MVP, not a
production-ready launch.

The repeatable Chromium suite in `tests/browser` covers the real API journey,
keyboard-only completion, 360px mobile layout, answer revision, sharing consent,
submission failure/retry, unlisted-result reopening in a new browser session,
clipboard fallback, and retakes. Desktop and mobile screenshots were also
inspected. Automated checks are not a full accessibility audit.

## 9. After the first working MVP

1. **More assessment types (first iteration implemented):** visual-detail
   matching, reasoning puzzles, and coordination practice. See section 10 for
   the agreed scope and completed checklist. Avoid unsafe physical tests.
2. **Combining tests:** define how multiple assessments contribute to an attribute,
   how incomplete coverage is displayed, and how versions affect comparisons.
   Do not silently average incompatible scores.
3. **Profiles and history (first iteration implemented):** optional accounts,
   result ownership, and private personal history. Deletion, recovery, and
   deliberate private-result sharing controls remain future work.
4. **Content management:** add authenticated administration and draft/publish
   workflows while preserving immutable published versions.
5. **Public deployment:** choose hosting, configure HTTPS and secrets, backups,
   restore checks, retention/deletion, abuse limits, monitoring, and deployment CI.
6. **Mobile client:** reuse the JSON API after the web product proves useful;
   decide whether to modernize the old Android project or build a new client.

## 10. Follow-up milestone: practice activities

Confirmed product decisions:

- Keep practice summaries separate; do not modify or combine S.P.E.C.I.A.L. scores.
- Provide six untimed visual-detail matches and six untimed reasoning puzzles.
- Provide twelve fixed-grid coordination rounds with optional elapsed timing and
  an untimed default. No countdown, moving targets, or physical exertion.
- Report task accuracy, mode, input method, and optional browser-reported duration,
  not standardized ability scores or comparisons between people.
- Explain device/accessibility influences, practice effects, and unverifiable timing.
- Preserve the anonymous/unlisted-link model and discard raw selections after scoring.

Implementation:

- [x] Add immutable versioned practice definitions and separate result storage
  through an additive Flyway migration; preserve existing definitions/results.
- [x] Add JSON APIs for catalogue, activity details, submission, and saved summaries.
- [x] Calculate accuracy server-side and reject invalid selections, modes, and timing.
- [x] Add catalogue links and browser flows for all three activities.
- [x] Support keyboard/pointer input, mobile layout, and untimed coordination.
- [x] Interrupt timing on tab/window focus loss; allow untimed continuation or restart.
- [x] Require sharing acknowledgement; preserve drafts on failed saves.
- [x] Provide separate unlisted result links, copy action, and retakes.
- [x] Exercise PostgreSQL/API contracts and all browser journeys, including the
  original assessment flow, save retry, timing recovery, and keyboard coordination.
- [x] Verify practice results survive application restart and DB container recreation.
- [x] Document practice APIs, timing semantics, storage, and limitations.

Practice definitions use a small typed JSON content document per version, rather
than extending questionnaire tables with task-specific columns. The existing
self-assessment/scoring schema and results remain unchanged.

## 11. Follow-up milestone: login and personal results

Confirmed product decisions:

- Local username/password registration and session-based login.
- Anonymous assessment/practice remains available.
- New signed-in submissions are private to the saving account.
- Existing and newly anonymous unlisted links keep working; do not claim them
  into accounts or change their access automatically.

Implementation:

- [x] Add an accounts table and nullable owner references with an additive migration.
- [x] Validate case-insensitive usernames and password length; hash passwords with
  salted PBKDF2 rather than storing credentials in plaintext.
- [x] Use server-managed sessions, rotate IDs on login, invalidate on logout,
  and protect all writes (including anonymous saves) with CSRF.
- [x] Enforce owner-only reads for both result types; do not offer public share
  links for private records.
- [x] Add registration/login forms, sign-out, session-aware privacy notices, and
  a combined newest-first, paginated My results screen.
- [x] Reject expired private sessions and cross-tab account/privacy changes on
  submission instead of silently publishing or misassigning a draft.
- [x] Verify registration validation, duplicate usernames, password hashes,
  session/token rotation, owner isolation, pagination, and anonymous compatibility.
- [x] Exercise browser journeys for both private result types, login/logout,
  empty history, duplicate registration, and expired-session draft retry.
- [x] Document sessions, CSRF, privacy rules, local-only limitations and APIs.

No email, recovery, account deletion, password change, or private-result
publication in this iteration. Session storage is in application memory;
accounts/results persist in PostgreSQL. A restart requires signing in again.
Review briefing privacy text at presentation time without changing immutable
question/scoring definitions or old results.

## 12. Separate stat quizzes and character sheet

Confirmed product decisions:
- Seven independent quizzes; the existing combined assessment and practice range remain unchanged.
- Strength/Endurance use original fitness self-reports informed by public guidance,
  not copied questionnaires or physiological/medical scores.
- Charisma uses questions/social scenarios. Photo presentation is optional future
  work after provider privacy review, never a source of personality/charisma scores.
- Perception and Intelligence use timed original tasks followed by untimed questions.
  Education/work context is optional, unscored and not persisted; there is no IQ conversion.
- Agility combines timed keyboard/touch/pointer coordination with everyday questions,
  with device/network/accessibility limitations made explicit.
- Luck combines perceived-opportunity questions with server-drawn coin/die games;
  outcomes are openly random and cannot predict real-world luck.
- A persistent character sheet requires sign-in. Use the latest individual quiz
  per stat, show untested values explicitly, retain all old history. Anonymous
  users can still take individual quizzes and receive unlisted result links.

Implementation:
- [x] Add immutable versioned definitions and summary storage through additive migrations.
- [x] Add server-timed session attempts, sequential answers and server-side chance draws.
- [x] Make task-choice/save retries idempotent and guard original account/privacy.
- [x] Add seven quiz briefings, task/question flows, results and character-sheet routes.
- [x] Include private stat results in mixed personal history without changing legacy scores.
- [x] Cover scoring bounds, exact timing boundaries, privacy, history/latest selection,
  validation, failures and original browser compatibility.
- [x] Add an optional private local-server photo screen, explicit storage consent,
  replacement/deletion, bounded metadata-stripped image storage and whole-image
  quality heuristics, separate from all stat scoring.
- [x] Add an optional iLook integration, disabled without a server-side key,
  with separate external-processing consent and adult authorization. Keep
  appearance feedback separate from all stat scoring and persistent history.
- [ ] Obtain an operator-issued iLook key, confirm multi-user application use,
  clarify downstream processing/retention/training/regions and verify a live call
  with a separately authorized adult image before enabling for other users.
- [ ] Validate/revise original content with domain experts before making any
  assessment-validity claims; review accessibility alternatives to timed tasks.

V1 task limits are Perception 75 seconds, Intelligence 180 seconds and Agility
45 seconds. These are game design choices, not validated assessment thresholds.
Mixed quizzes use 70% task outcomes and 30% reflection; taskless quizzes use 100%
reflection. Repeated fixed content involves learning. Published definitions
and scoring are immutable; updates require new versions/migrations.

## 13. Private local photos

The user selected server-side storage under the signed-in account rather than
browser-only storage. V8 adds one normalized PNG per account in PostgreSQL.
An authenticated, CSRF-protected photo screen supports explicit consent, preview,
save/replace, delete, local brightness/detail/resolution feedback, and expected-
account checks. iLook analysis is a separate opt-in action, disabled without a key;
local storage never automatically sends images externally.
Feedback is whole-image only and never updates S.P.E.C.I.A.L. Photos persist in
the existing local database volume; deletion only removes the active row and
cannot purge existing backups/WAL. Upload sizes, decoded dimensions and normalized
output are bounded. Local-only storage is not a public-deployment security claim.

Current provider research and source links are in README. Face++ advertises beauty
attributes, but a current free quota and fixed photo-retention period could not
be verified. Luxand advertises a trial rather than permanent free use. Rekognition
offers photo quality, not beauty scores; MediaPipe is a possible future local
landmark detector. Do not present appearance scores or "blackpill" claims as
scientific measures of worth, character or prospects.

iLook publishes a multipart API and 100 requests/key/UTC-day allowance with
manually issued beta keys. The integration shows versioned subjective appearance
feedback transiently in the photo screen, requires separate adult/third-party
consent and the exact saved revision, and does not change Charisma. Credentials
stay server-side. No key or live provider image request was used during implementation.

## 14. Next implementation session

Review the individual quiz experience and question wording locally. Provider
key provisioning/data-processing review, recovery/deletion, accessible alternative assessment
versions and public deployment remain separate follow-up decisions.
Keep `FalloutCalculator` unchanged unless explicitly authorized.
