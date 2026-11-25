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

UI: light mode by default (operating-system dark preference ignored), optional dark mode saved to the user profile, IBM Plex Sans, navy navigation, blue actions, translucent surfaces, Motion transitions and counters, and a shift-timeline strip showing sessions, breaks (over-limit in red) and inactivity separately. Responsive to 390 px; keyboard focus visible; reduced motion respected. Employees see an explicit statement of what is recorded.

## Known limitations

1. **Idle detection is browser-only** (activity inside the SUMA tab). System-wide idle detection needs a separately approved desktop agent; none is installed and the UI says so.
2. **Closing a tab is not treated as a logout** (it would also fire on refresh). Sessions without heartbeat for 30 min are closed at the last heartbeat and marked missing logout.
3. **Integrations** were not verified against real systems (no credentials) and have no automated tests.
4. **Automation actions** have no dedicated integration tests (conditions are unit-tested; demo rules execute at seed time).
5. Notification rules, custom ticket field definitions and dashboard widget toggles have database/API support but **no admin UI** yet.
6. Some endpoints validate the request body before checking permissions, so an unauthorised caller may get 400 instead of 403 for an invalid body.
7. Session revocation is cached for up to 20 s per backend node; the login rate limiter is per node.
8. Monthly PDF export contains the summary only; the Excel export carries daily details, every break timing and idle events.
9. Attendance does not yet apply holiday calendars (needs a business decision).
10. In E2E, clicking a ticket action during the page transition could miss; the test waits for the page to settle. Not reproduced manually.
11. Approximate IP geolocation is not enabled (no approved provider).

## Blocked tasks
- Live verification of Jira / Salesforce / IMAP connectors — needs endpoints and credentials.
- Real SMTP delivery — needs the company relay; development uses Mailpit, tests use GreenMail.
- Desktop agent for system-wide idle detection — needs approval and privacy review.
- Geolocation — needs an approved provider.
- Public deployment — not performed (out of scope for unattended work).

## Exact next steps
1. Run the `security-review` and `code-review` skills on the full diff and fix findings.
2. Add admin UI for notification rules, custom ticket fields and dashboard widget toggles.
3. Add integration tests for automation actions and WireMock-based connector tests.
4. Push and confirm the GitHub Actions workflow passes; build the multi-stage images in CI.
5. Decide the open business rules in PROJECT_PLAN.md (break policy, holidays in attendance, retention, employee disputes).
6. Configure SMTP and verify Project Lead emails before the next 1st of the month.
7. Reconcile with the original requirement PDF once it is added to `docs/requirements/`.

## How to run
See README.md. Default admin: `admin` / `Admin@12345` in development only. Demo data (`SUMA_DEMO_DATA=true`) adds three clients, three projects, 13 employees, 70 tickets and two weeks of attendance; demo users use `Password@123`.
