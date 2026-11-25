# SUMA Tracking Tool — Project plan and architecture

## Source requirements

The original requirement PDF and screenshots were **not supplied** to this session (the repository was empty). With the
product owner's agreement, the master prompt is treated as the full specification. When the PDF is available it should be
placed in `docs/requirements/` and reconciled against this plan; deviations should be logged in PROJECT_PROGRESS.md.

## Architecture

Modular monolith: one Spring Boot application organised by business domain, one React SPA, one PostgreSQL database.

```
Browser (React SPA, in-memory access token, HttpOnly refresh cookie)
   │  /api/v1  (JSON, Bearer JWT)                     nginx (static files, reverse proxy, overwrites X-Forwarded-For)
   ▼
Spring Boot ── filters: CorrelationId → IpRule (allow/deny) → JWT + session validation → Spring Security (RBAC) → controllers
   │            entry point / access-denied handler record unauthenticated and unauthorized requests with IP
   ├─ domain services (auth, client, project, user, team, ticket, sla, attendance, ipmonitoring, integration,
   │                   automation, report, notification, knowledge, audit, settings, dashboard)
   ├─ scheduler (Spring @Scheduled + ShedLock, job_execution_log)
   ├─ email outbox → SMTP (retry with backoff)          external: Jira / Salesforce / REST / IMAP connectors, RPA webhooks
   ▼
PostgreSQL (Flyway V1–V7)
```

Backend packages (`backend/src/main/java/com/suma`): `config`, `common`, `security`, `auth`, `audit`, `user`, `client`,
`project`, `team`, `ticket`, `sla`, `attendance`, `ipmonitoring`, `integration`, `automation`, `report`, `notification`,
`knowledge`, `settings`, `dashboard`, `scheduler`.

Frontend (`frontend/src`): `components/ui` (shadcn-style primitives), `components` (shared), `layouts`, `modules/<module>`,
`contexts` (auth, theme, attendance), `hooks`, `services/api.ts`, `routes/nav.ts`, `types`, `utils`, `styles`.

### Key design decisions

| Concern | Decision |
|---|---|
| Authentication | JWT access token (15 min, memory only) + opaque refresh token (HttpOnly, SameSite=Strict cookie, SHA-256 hashed in DB, rotated on every use, reuse ⇒ session revoked). Each login is a `user_sessions` row; the token carries its id so sessions can be terminated immediately. |
| Passwords | BCrypt cost 12; temporary lock after 5 failed attempts (configurable); identical error for unknown user / wrong password / disabled account. |
| Authorization | 6 roles × 33 permissions (editable), `@PreAuthorize` on every endpoint, plus data scoping (`AccessScopeService`) by project/client/team. |
| Project Lead | Per-project designation + verified report email, not a 7th role. |
| Client IP | `ClientIpResolver`: `X-Forwarded-For` honoured only from configured trusted proxy CIDRs, walked right-to-left; IPv4/IPv6 normalised. |
| Attendance | One attendance session per login; breaks and idle events belong to a session; concurrent sessions merged (interval union) in reports; every change writes an immutable `attendance_events` row; corrections keep the original value. |
| Idle detection | Browser activity signals (keyboard, pointer, touch, wheel, scroll, visibility) inside the SUMA tab; heartbeat every 60 s carries last activity; prompt at threshold (default 20 min). Server-side fallback from heartbeats. Idle ≠ break unless the employee says so; unconfirmed idle is never penalised automatically. **No system-wide monitoring** — that would need a separately approved desktop agent. |
| Missing logout | Closing a tab is not treated as a logout. Sessions without a heartbeat for the configured timeout are closed at the **last heartbeat** and marked `MISSING_LOGOUT`; the employee is notified. |
| Monthly report | Idempotent claim row per (project, month) → generate Excel → email outbox → retry with exponential backoff → status mirrored to the report log. Only verified Project Lead addresses. Catch-up after downtime. |
| Scheduling | ShedLock (DB) + job history; jobs derive work from DB state and are idempotent. |
| SLA | Business-calendar arithmetic (hours, holidays, timezone/DST), pause while PENDING, breach detection job, percentage and breach escalations. |
| Automation | TRIGGER → CONDITIONS → ACTIONS rules evaluated synchronously inside the ticket transaction; re-entrancy guard; execution log. |
| Integrations | `TicketConnector` interface (Jira, Salesforce, REST, IMAP email), dot-path field mapping, AES-GCM encrypted credentials never returned, per-record transactions, duplicate prevention by unique (system, external id), retries, failure alerts. |
| UI | Light mode default (OS dark preference ignored), optional dark mode persisted per user; IBM Plex Sans; navy navigation, blue actions, translucent "glass" surfaces; Motion for page transitions, counters, dialogs, nav indicator; signature shift timeline (sessions / breaks / inactivity) for attendance. |

## The 14 modules and where they live

| # | Module | Backend | Frontend |
|---|---|---|---|
| 1 | Dashboard (incl. workforce widgets) | `dashboard` | `modules/dashboard` |
| 2 | Client management | `client` | `modules/clients` |
| 3 | Project management (Project Lead, categories, staffing) | `project` | `modules/projects` |
| 4 | User & team management (shifts, weekly offs, workload) | `user`, `team` | `modules/users` |
| 5 | Ticket management | `ticket` | `modules/tickets` |
| 6 | SLA management | `sla`, `ticket/SlaMonitorService` | `modules/sla` |
| 7 | Integration management | `integration` | `modules/integrations` |
| 8 | Workflow & automation (+ scheduled attendance automations) | `automation`, `scheduler`, `attendance/AttendanceMaintenanceService` | `modules/automation` |
| 9 | Reports & analytics (operational + attendance) | `report`, `attendance/AttendanceReportService`, exports | `modules/reports`, attendance team page |
| 10 | Notifications & escalations | `notification` | top bar, `modules/notifications`, settings email log |
| 11 | Knowledge base & SOP | `knowledge` | `modules/knowledge` |
| 12 | Security & audit | `security`, `audit`, `ipmonitoring` | `modules/security`, `modules/audit` |
| 13 | System configuration | `settings`, attendance settings, roles | `modules/settings` |
| 14 | Attendance & break monitoring + login security / IP monitoring | `attendance`, `ipmonitoring` | `modules/attendance`, `modules/security` |

## Assumptions made (to confirm with the business)

1. Six roles are fixed; Project Lead is a project designation.
2. Default timezone for attendance reporting is UTC in a fresh install (demo data sets Asia/Kolkata). Each organisation sets its own.
3. Break limits, daily break limits, maximum breaks per day, reminder intervals and default work hours are **not preset** (NULL = no limit) because no company policy was supplied. Only the 20-minute idle threshold has a default, as specified.
4. Heartbeat timeout 30 min and maximum session length 16 h are technical defaults for detecting closed browsers; configurable.
5. A "present" day is any day with at least one session in the reporting timezone; a day without a session on a non-weekly-off day is reported as `NO_LOGIN_RECORD` (holiday calendars are not yet applied to attendance — see open rules).
6. L1–L3 see tickets assigned to them, unassigned tickets of their projects, and tickets they created.
7. Default SLA calendar is 24×7 until business hours are configured; no SLA targets are preset (demo data adds sample ones).
8. Monthly reports go out on the 1st at 06:00 in the reporting timezone (configurable hour), as one Excel workbook per project (summary, daily details, every break timing, idle events).
9. Email is delivered through a configurable SMTP relay; development uses Mailpit and tests use GreenMail. No real recipients are emailed without configuration.
10. Approximate IP geolocation is not enabled (no approved provider); the `geo_location` column exists for a future provider.

## Open business rules (need a decision)

- Official break policy (durations, counts, paid/unpaid categories) and whether exceeding it has HR consequences.
- Whether public holidays from the SLA business calendar should also excuse attendance.
- Whether Project Leads should also receive daily summaries.
- Retention periods required by the company's privacy policy and local labour law.
- Whether employees may correct or dispute their own attendance (currently only administrators correct, employees are notified).
- Whether system-wide idle detection (desktop agent) is wanted; it requires separate approval, installation and privacy review.
- Real Jira / Salesforce / mailbox endpoints and credentials for integration verification.

## Roadmap (planning estimate)

| Phase | Scope | Status (see PROJECT_PROGRESS.md) |
|---|---|---|
| 1 Foundation | repo, Vite/React, Spring Boot, PostgreSQL, Flyway, Docker, design system, layouts | Completed |
| 2 Auth & security | auth, 6 roles, RBAC, tokens, login activity, IP capture | Completed |
| 3 Clients & projects | clients, projects, account managers, Project Leads, configuration | Completed |
| 4 Users, teams, attendance foundation | employees, teams, shifts, weekly offs, policies, attendance session design | Completed |
| 5 Tickets & breaks | ticket lifecycle, login/logout tracking, breaks, attendance dashboard | Completed |
| 6 SLA & idle | SLA engine, calendars, 20-minute idle prompts, reminders, exceptions | Completed |
| 7 Integrations & automation | connectors, sync, rule engine, attendance automations, RPA adapter | Completed (live connectors unverified — no credentials) |
| 8 Reports | operational and attendance reports, Excel/CSV/PDF | Completed |
| 9 Notifications & security | notifications, monthly delivery, IP dashboard, anonymous monitoring, KB, audit, configuration | Completed |
| 10 QA & deployment | tests, hardening, Docker, CI, docs | Largely completed — see report for remaining items |
