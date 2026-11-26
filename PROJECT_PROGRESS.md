# Project progress

Statuses: NOT STARTED · IN PROGRESS · BLOCKED · TESTING · COMPLETED. "Tested" refers to automated tests that were executed
and passed in this environment (see OVERNIGHT_DEVELOPMENT_REPORT.md for the exact runs). Last updated: 2026-11-26.

| ID | Module | Feature | Depends on | Priority | Status | Test status | Known issues |
|---|---|---|---|---|---|---|---|
| F-01 | Foundation | Repo layout, Maven/Vite projects, Flyway V1–V7, design tokens, layouts | — | P0 | COMPLETED | build ✔ | — |
| F-02 | Foundation | Dockerfiles, docker-compose (PostgreSQL, Mailpit, backend, nginx), CI workflow | F-01 | P1 | TESTING | runtime images run ✔; multi-stage build not run in sandbox; CI not yet observed on GitHub | in-container dependency download needs normal internet |
| A-01 | Security | Login, refresh rotation with reuse detection, logout, me, preferences, password change | F-01 | P0 | COMPLETED | IT ✔ E2E ✔ | — |
| A-02 | Security | 6 roles, 33 permissions, editable matrix, @PreAuthorize everywhere | A-01 | P0 | COMPLETED | IT ✔ E2E ✔ unit ✔ | some endpoints validate body before authorising (400 before 403) |
| A-03 | Security | Data isolation by project/client/team | A-02 | P0 | COMPLETED | IT ✔ (tickets) | — |
| A-04 | Security | Account lock after repeated failures, login rate limiting | A-01 | P1 | COMPLETED | IT ✔ unit ✔ | rate limiter is per node (in memory) |
| S-01 | IP monitoring | Successful/failed/unknown-user login logs with IPv4/IPv6, device, browser | A-01 | P0 | COMPLETED | IT ✔ E2E ✔ | — |
| S-02 | IP monitoring | Anonymous / invalid / expired / revoked / access-denied / blocked request logging with correlation id | A-01 | P0 | COMPLETED | IT ✔ E2E ✔ | — |
| S-03 | IP monitoring | Trusted-proxy IP extraction | — | P0 | COMPLETED | unit ✔ IT ✔ docker ✔ | — |
| S-04 | IP monitoring | Suspicious indicators, repeated-failure detection, IP change detection (never blocks) | S-01 | P1 | COMPLETED | IT ✔ | — |
| S-05 | IP monitoring | Admin security dashboard, searches, filters, CSV exports | S-01 | P0 | COMPLETED | E2E ✔ | — |
| S-06 | IP monitoring | Session termination, IP allow/deny rules | A-01 | P0 | COMPLETED | IT ✔ E2E ✔ | session cache: up to 20 s on other nodes |
| S-07 | IP monitoring | Approximate geolocation | provider | P3 | BLOCKED | — | needs an approved geolocation provider |
| O-01 | Clients | CRUD, contacts, account manager, client configuration, ticket counts | A-02 | P1 | COMPLETED | smoke ✔ | — |
| O-02 | Projects | CRUD, manager/TL/Project Lead, lead email verification, members, categories, support hours | O-01 | P0 | COMPLETED | E2E ✔ (lead routing) | — |
| O-03 | Users & teams | Employees, roles, shifts, weekly offs, teams, workload, password reset, unlock | A-02 | P1 | COMPLETED | E2E ✔ (create user) | — |
| T-01 | Tickets | Create, assign, lifecycle, comments, attachments, history, escalation, ageing | O-02 | P0 | COMPLETED | IT ✔ E2E ✔ | — |
| T-02 | Tickets | External ingestion with duplicate prevention | T-01 | P1 | COMPLETED | IT ✔ | — |
| L-01 | SLA | Policies per priority/project, business calendars, holidays, pause on pending | T-01 | P0 | COMPLETED | unit ✔ IT ✔ | — |
| L-02 | SLA | Breach detection, alerts, percentage/breach escalations | L-01 | P0 | COMPLETED | IT ✔ | — |
| W-01 | Attendance | Session per login, logout sources, concurrent sessions | A-01 | P0 | COMPLETED | unit ✔ IT ✔ E2E ✔ | — |
| W-02 | Attendance | Breaks: start/end/current/history, categories, policies, limits | W-01 | P0 | COMPLETED | IT ✔ E2E ✔ | — |
| W-03 | Attendance | Break reminders, exceeded alerts, follow-ups | W-02 | P0 | COMPLETED | IT ✔ | — |
| W-04 | Attendance | 20-minute in-app idle detection, prompt, confirmation, team-leader notification, server fallback | W-01 | P0 | COMPLETED | IT ✔ E2E ✔ | browser-only scope by design |
| W-05 | Attendance | Missing-logout reconciliation (heartbeat timeout, max session length, session expiry) | W-01 | P0 | COMPLETED | IT ✔ | — |
| W-06 | Attendance | Administrative corrections preserving originals | W-01 | P1 | COMPLETED | IT ✔ | — |
| W-07 | Attendance | Daily & monthly reports, filters, Excel/CSV/PDF exports | W-01 | P0 | COMPLETED | unit ✔ IT ✔ E2E ✔ | monthly PDF contains the summary sheet only (Excel has all details) |
| W-08 | Attendance | Monthly Project Lead email: idempotent, verified recipients, retries, logs, manual resend | W-07 | P0 | COMPLETED | IT ✔ (GreenMail) E2E ✔ | — |
| W-09 | Attendance | Attendance dashboard, employee / TL / admin / Project Lead views, transparency notice | W-01 | P0 | COMPLETED | E2E ✔ | — |
| W-10 | Attendance | Daily summaries, exception notifications, monthly summaries | W-07 | P1 | COMPLETED | compiled, run by scheduler | no dedicated test |
| W-11 | Attendance | Holiday calendar applied to attendance | business rule | P2 | BLOCKED | — | awaiting decision (see PROJECT_PLAN.md) |
| W-12 | Attendance | System-wide idle detection via desktop agent | approval | P3 | BLOCKED | — | requires separate approval and privacy review |
| D-01 | Dashboard | KPIs, trends, priority/status, team performance, workforce widgets, filters | T-01, W-01 | P0 | COMPLETED | smoke ✔ visual ✔ | — |
| R-01 | Reports | Productivity, SLA, pending, ageing, daily/weekly/monthly, client, project, team + CSV/Excel | T-01 | P1 | COMPLETED | smoke ✔ | — |
| N-01 | Notifications | In-app notifications, email outbox with retry, rules API, email log UI | — | P0 | COMPLETED | IT ✔ | no UI yet for notification rules (API only) |
| K-01 | Knowledge | Articles, categories, full-text search, usage tracking, helpful votes | — | P1 | COMPLETED | smoke ✔ | — |
| M-01 | Automation | Rule engine, conditions, 12 action types, execution log, approvals, RPA webhook adapter | T-01 | P1 | COMPLETED | unit ✔ (conditions), demo executions | actions lack dedicated integration tests |
| I-01 | Integrations | Jira, Salesforce, REST, IMAP connectors, mapping, encryption, retries, logs | T-02 | P1 | TESTING | not tested against real systems | BLOCKED on credentials for live verification |
| C-01 | Configuration | Attendance settings (global + project), break policies, system settings, statuses/priorities, email templates, roles, job history | — | P0 | COMPLETED | E2E (render) ✔ | custom ticket field definitions and dashboard widget toggles have DB/API support but no admin UI |
| U-01 | UI | Light default, dark mode, responsive (390 px), motion | — | P0 | COMPLETED | Vitest ✔ E2E ✔ visual ✔ | — |
| U-02 | UI | Redesign "Bento + soft depth": Sora/DM Sans, indigo→violet brand gradient, floating collapsible sidebar, Ctrl+K command palette, bento dashboard, shift ring, avatar status cards, masonry knowledge base, security hero gauge | U-01 | P1 | COMPLETED | typecheck ✔ Vitest 12/12 ✔ E2E 14/14 ✔ 390 px overflow ✔ | — |
| U-03 | UI | Bento layouts on every remaining page: hero banners, filter bars, card/table toggles (tickets, clients, projects, employees), chat-style ticket conversation with SLA gauge, people/team cards, workload leaderboard, 24 h shift tracks, SLA policy cards and calendar day pills, automation WHEN→IF→THEN flow cards, connector cards, day-grouped audit timeline, grouped notifications, profile hero, attendance summary tiles and delivery cards, security feeds and IP rule cards, configuration section tiles and permission matrix | U-02 | P1 | COMPLETED | typecheck ✔ Vitest 12/12 ✔ build ✔ E2E 14/14 ✔ 390 px overflow ✔ (17 pages) | data tables kept where E2E or bulk scanning needs rows (daily/monthly attendance, sign-ins, unauthenticated, sessions) |
| Q-01 | QA | Backend unit + integration tests | — | P0 | COMPLETED | 50/50 ✔ | — |
| Q-02 | QA | Frontend Vitest | — | P1 | COMPLETED | 12/12 ✔ | — |
| Q-03 | QA | Playwright E2E | — | P0 | COMPLETED | 14/14 ✔ | — |
| Q-04 | QA | Security review of full diff | — | P1 | NOT STARTED | — | recommended next |
| X-01 | Docs | README, plan, schema, API, roles, skills, progress, deployment, handover | — | P0 | COMPLETED | — | — |
