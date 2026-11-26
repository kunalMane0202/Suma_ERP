# Overnight development report — SUMA Tracking Tool

Session date: 2026-11-25. Branch: `ccr-d0b30e30-1y5532`. Starting point: an empty repository; the requirement PDF and
screenshots were not available, so the master prompt was used as the specification (agreed with the product owner).

## Summary

All 14 modules have a working backend and frontend, backed by PostgreSQL through Flyway migrations. The priority areas —
attendance, breaks, 20-minute idle prompts, attendance reports, monthly Project Lead email delivery and login security /
IP monitoring — are implemented and covered by automated tests that were executed and passed. The platform is **not
production-complete**: integrations have not been verified against real Jira/Salesforce/mail systems, a security review of
the full code has not been run yet, and a few admin screens are API-only (listed below).

## Build and test results (executed in this session)

| Check | Command | Result |
|---|---|---|
| Backend unit tests | `mvn verify` (surefire) | **24 passed, 0 failed** |
| Backend integration tests (PostgreSQL, MockMvc, GreenMail) | `mvn verify` (failsafe) | **26 passed, 0 failed** — BUILD SUCCESS |
| Frontend type check | `npm run typecheck` | passed |
| Frontend unit/component tests | `npx vitest run` | **12 passed, 0 failed** |
| Frontend production build | `vite build` | passed (largest chunk: charts 424 kB / 114 kB gzip) |
| End-to-end (Playwright, Chromium) | `npx playwright test --trace off` | **14 passed, 0 failed** (covers the 20 required flows) |
| Container runtime | images built from local artifacts; PostgreSQL + backend + nginx containers | app served, login through nginx proxy recorded the correct client IP |

Not executed: the multi-stage Dockerfiles end-to-end (dependency downloads inside containers need ordinary internet access,
which this sandbox routes through an intercepting proxy) and the GitHub Actions workflow (runs on push).

Fixes made because tests or inspection found real problems:
- Live attendance table showed times in the browser timezone instead of the reporting timezone.
- Days before an employee's account existed were reported as "No login record".
- Today's compliance counted employees whose shift had not started.
- Grid layouts overflowed horizontally at phone width (fixed in the Card primitive).
- nginx appended to a client-supplied `X-Forwarded-For`, allowing a spoofed IP when the client sits inside a trusted range; nginx now overwrites the header.
- Productivity report SQL grouping error; knowledge-base search rank type error.

## What was built

### Backend (Spring Boot 3.4, Java 21) — 202 source files, 161 REST operations
- **Auth & sessions:** JWT access tokens + rotating HttpOnly refresh cookie with reuse detection, per-login sessions, terminate, account lock, login rate limit, BCrypt.
- **RBAC & isolation:** 6 roles, 33 editable permissions, `@PreAuthorize` on every endpoint, project/client/team data scoping, Project Lead designation with verified report email.
- **Modules:** clients, projects, users/teams/shifts/workload, tickets (lifecycle, comments, attachments, timeline, escalation, external dedupe), SLA (business calendars, holidays, DST-safe arithmetic, pause, breaches, escalations), dashboard, operational reports with CSV/Excel, knowledge base with PostgreSQL full-text search, automation rule engine with approvals and RPA webhook adapter, integrations (Jira, Salesforce, REST, IMAP) with encrypted credentials, notifications + email outbox, audit trail, system configuration.
- **Attendance engine:** session per login, breaks, categories, policies, reminders/exceeded/follow-ups, in-app idle detection with confirmation and server fallback, missing-logout reconciliation at last heartbeat, corrections with preserved originals, daily/monthly reports, Excel/CSV/PDF exports, daily/monthly summaries, exception notifications.
- **Monthly Project Lead reports:** idempotent per project and month, verified recipients only, Excel attachment, exponential-backoff retries, delivery log, manual (forced) resend, catch-up after downtime.
- **Login security & IP monitoring:** successful/failed/unknown-user logins, anonymous/invalid/expired/revoked/denied/blocked requests with correlation IDs, trusted-proxy IP resolution (IPv4/IPv6), suspicious indicators, IP-change detection that never blocks, dashboard, searches, CSV exports, IP allow/deny rules.
- **Scheduler:** 10 jobs with ShedLock and execution history (see docs/deployment/DEPLOYMENT.md).

### Database — Flyway V1–V7, 61 tables
Includes every table requested in the brief (attendance_sessions, attendance_events, break_records, break_policies, break_reminders, idle_events, attendance_daily_summary, attendance_monthly_summary, attendance_report_logs, attendance_settings, login_activity_logs, anonymous_access_logs, ip_activity_logs, trusted_ip_rules, session_activity_logs) with unique constraints that prevent duplicate sessions, breaks, idle events, reminders, external tickets and monthly deliveries. See DATABASE_SCHEMA.md.

### Frontend (React 18 + Vite + TypeScript) — 20 pages
Sign-in · Dashboard · Tickets · Ticket detail · My attendance · Team attendance (live, daily, monthly, Project Lead reports, history & corrections) · Clients · Projects · Project detail · People & teams (employees, teams, workload, shifts) · SLA · Reports · Knowledge base · Automation · Integrations · Login security (overview, sign-ins, unauthenticated, IP activity, sessions, IP rules) · Audit trail · Configuration (attendance, break policies, system, templates, roles & permissions, jobs, email delivery) · Notifications · Profile.

UI (redesigned 2026-11-26, "Bento + soft depth"): lavender canvas, white bento tiles whose size reflects importance, indigo→violet gradient used for the hero tile, active navigation and primary actions; Sora headings/numerals with DM Sans body; floating collapsible sidebar; Ctrl+K command palette for pages and tickets; shift ring on My attendance; avatar status cards on Team attendance; Pinterest-style masonry knowledge base; sign-in page with floating preview tiles. Phase 2 (same day) gave every remaining page its own bento layout: hero banners with headline numbers, pill filter bars, card/table toggles on list pages, chat-style ticket conversations with an SLA gauge, people and team cards, a workload leaderboard, 24-hour shift tracks, SLA policy cards, WHEN→IF→THEN automation flow cards, connector cards, a day-grouped audit timeline, grouped notifications and a profile hero. Re-verified after phase 2: typecheck, Vitest 12/12, production build, E2E 14/14 and no horizontal overflow at 390 px on 17 pages. Earlier baseline: light mode by default (operating-system dark preference ignored), optional dark mode saved to the user profile, IBM Plex Sans, navy navigation, blue actions, translucent surfaces, Motion transitions and counters, and a shift-timeline strip showing sessions, breaks (over-limit in red) and inactivity separately. Responsive to 390 px; keyboard focus visible; reduced motion respected. Employees see an explicit statement of what is recorded.

## Skill audits (2026-11-26)

Run after the redesign with the installed skills; each finding was fixed and re-verified.

| Skill | Findings fixed |
|---|---|
| web-design-guidelines | Clickable cards were divs (now real links with Cmd/Ctrl+click and keyboard); no skip link; five deletes saved without confirmation; `transition-all`; logo images without size; missing theme-color, overscroll and touch settings |
| ui-ux-pro-max (axe-core 4.10 on 18 pages, light and dark) | 242 contrast failures from two causes: muted text at 80% opacity and status colours used as text. Added text-only status shades; heading levels; labelled scroll regions; chart semantics; sign-in landmarks. Result: 0 violations in both themes |
| vercel-react-best-practices | Sign-in page loaded eagerly with zod and react-hook-form (main chunk 400 kB to 303 kB, 127 to 99 kB gzip); duplicate in-flight lookup requests now shared |
| dataviz (validator script) | Green/red series indistinguishable with red-green colour blindness; priority and ageing used unrelated hues for ordered data. Now validated series colours and one-hue ordinal ramps per theme; legends on every multi-series chart |
| security review (focused) | Reports CSV export missed '-' formula prefixes and all CSV exports missed tab/CR; integration and RPA URLs could target cloud metadata and loopback. Added `SpreadsheetSafety` and `OutboundUrlGuard` with unit tests |

## Remaining-work pass (2026-11-26)

| Area | Done | Verified by |
|---|---|---|
| Authorisation order | `EarlyAuthorizationInterceptor` evaluates `@PreAuthorize` before the body is read: unauthorised callers get 403, not validation details | EarlyAuthorizationIT |
| Shareable URLs | Tabs, filters and page kept in the address on 8 pages (`useUrlState`, `UrlTabs`) | E2E (tab survives reload), browser check |
| Health | Mail health indicator off: an SMTP outage no longer reports the app DOWN | `/actuator/health` = UP with SMTP down |
| Monthly PDF | Summary plus daily details, break timings and idle events | AttendanceIT (PDF text and page count) |
| Admin screens | Custom ticket fields (API + typed validation + form + details), notification rules (now also escalations, critical incidents, approvals), dashboard widget toggles | CustomFieldIT, Vitest, E2E, browser check |
| Tests | All 12 automation actions; Jira/Salesforce/REST connectors; attendance summary jobs | AutomationActionsIT, ConnectorIT, AttendanceSummaryIT |
| Security review | Delegated USER_WRITE/ROLE_MANAGE can no longer create or take over admins or grant unheld permissions; password reset/change signs out other sessions; post-login redirect restricted to in-app paths; startup warning for public API docs in production; axios and react-router patched | DelegatedAdminIT, manual checks |
| Bugs found by the new tests | Blank external titles created empty tickets (now "(no subject) <id>"); inactive items dimmed below contrast minimum | ConnectorIT, axe |

Results after the pass: backend **75/75**, Vitest **16/16**, E2E **16/16**, production build passes, axe **0 violations** on 21 views in light and dark.

## Known limitations

1. **Idle detection is browser-only** (activity inside the SUMA tab). System-wide idle detection needs a separately approved desktop agent; none is installed and the UI says so.
2. **Closing a tab is not treated as a logout** (it would also fire on refresh). Sessions without heartbeat for 30 min are closed at the last heartbeat and marked missing logout.
3. **Integrations** were not verified against real systems (no credentials). Jira, Salesforce and REST connectors are tested against a local stub server; the email (IMAP) connector has no automated test.
4. Session revocation is cached for up to 20 s per backend node; the login rate limiter is per node.
5. Attendance does not yet apply holiday calendars (needs a business decision).
6. npm audit: zod (memory use on oversized input, client-side forms only) and react-router (open redirect via backslash paths; the app's only redirect is now restricted to in-app paths) need major-version upgrades; not done yet.
7. Backend dependency vulnerability scan (OWASP dependency-check) not run in this environment.
8. In E2E, clicking a ticket action during the page transition could miss; the test waits for the page to settle. Not reproduced manually.
9. Approximate IP geolocation is not enabled (no approved provider).

## Blocked tasks
- Live verification of Jira / Salesforce / IMAP connectors — needs endpoints and credentials.
- Real SMTP delivery — needs the company relay; development uses Mailpit, tests use GreenMail.
- Desktop agent for system-wide idle detection — needs approval and privacy review.
- Geolocation — needs an approved provider.
- Public deployment — not performed (out of scope for unattended work).

## Exact next steps
1. Decide the open business rules in PROJECT_PLAN.md (break policy, holidays in attendance, retention, employee disputes).
2. Provide Jira / Salesforce / mailbox endpoints and credentials to verify integrations live; add an IMAP test with a trusted test certificate.
3. Configure the company SMTP relay and verify Project Lead emails before the next 1st of the month.
4. Upgrade zod (3 → 4) and react-router (6 → 7) in a dedicated change; run OWASP dependency-check on the backend in CI.
5. Push and confirm the GitHub Actions workflow; build the multi-stage images in CI.
6. For more than one backend node: move the login rate limiter and session-revocation cache to a shared store.
7. Reconcile with the original requirement PDF once it is added to `docs/requirements/`.

## How to run
See README.md. Default admin: `admin` / `Admin@12345` in development only. Demo data (`SUMA_DEMO_DATA=true`) adds three clients, three projects, 13 employees, 70 tickets and two weeks of attendance; demo users use `Password@123`.
