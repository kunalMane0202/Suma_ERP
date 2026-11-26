# SUMA Tracking Tool — API documentation

Base path: `/api/v1`. Interactive documentation: `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`, snapshot in `docs/api/openapi.json`).

## Conventions

- **Authentication:** `POST /api/v1/auth/login` returns a short-lived JWT access token (send as `Authorization: Bearer …`) and sets an HttpOnly, SameSite=Strict refresh cookie `suma_rt` scoped to `/api/v1/auth`. `POST /api/v1/auth/refresh` rotates the refresh token; re-use of an already-rotated token revokes the whole session.
- **Authorization:** every endpoint is checked on the server. The *Access* column shows the required permission (see ROLE_PERMISSION_MATRIX.md). `authenticated` means any signed-in user; results are additionally filtered to the caller's clients/projects/teams (data isolation).
- **Errors:** JSON `{timestamp, status, code, message, path, correlationId, fieldErrors}`. Every response carries `X-Correlation-Id`.
- **Times:** ISO-8601 UTC instants; dates `yyyy-MM-dd`; attendance days use the configured reporting timezone.
- **Paging:** list endpoints accept `page` (0-based) and `size` and return `{content, page, size, totalElements, totalPages}`.

## Authentication

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/auth/change-password` | authenticated |  |
| POST | `/api/v1/auth/login` | public | Authenticate with username/email and password |
| POST | `/api/v1/auth/logout` | public | End the current session (idempotent). source=BROWSER_UNLOAD for best-effort tab-close beacons. |
| GET | `/api/v1/auth/me` | authenticated |  |
| POST | `/api/v1/auth/password-reset-requests` | public | Send a single-use, expiring password reset link when the email belongs to an active account; response does not reveal account existence |
| POST | `/api/v1/auth/password/complete` | public | Complete account setup or password reset using the one-time token |
| PUT | `/api/v1/auth/me/preferences` | authenticated |  |
| POST | `/api/v1/auth/registration-requests` | public | Request an account for administrator review |
| POST | `/api/v1/auth/refresh` | public | Rotate refresh token and obtain a new access token |

## Dashboard

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | `DASHBOARD_VIEW` |  |

## Clients

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/clients` | `CLIENT_READ` |  |
| POST | `/api/v1/clients` | `CLIENT_WRITE` |  |
| GET | `/api/v1/clients/{id}` | `CLIENT_READ` |  |
| PUT | `/api/v1/clients/{id}` | `CLIENT_WRITE` |  |
| PATCH | `/api/v1/clients/{id}/status` | `CLIENT_WRITE` |  |

## Projects

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/projects` | `PROJECT_READ` |  |
| POST | `/api/v1/projects` | `PROJECT_WRITE` |  |
| GET | `/api/v1/projects/{id}` | `PROJECT_READ` |  |
| PUT | `/api/v1/projects/{id}` | `PROJECT_WRITE` |  |
| GET | `/api/v1/projects/{id}/categories` | `PROJECT_READ` |  |
| POST | `/api/v1/projects/{id}/categories` | `PROJECT_WRITE` |  |
| DELETE | `/api/v1/projects/{id}/categories/{categoryId}` | `PROJECT_WRITE` |  |
| POST | `/api/v1/projects/{id}/lead-email/verify` | `PROJECT_WRITE` | Mark the Project Lead report email as verified (or unverified). Monthly reports are only sent to verified recipients. |
| PUT | `/api/v1/projects/{id}/members` | `PROJECT_WRITE` |  |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | `PROJECT_WRITE` |  |
| PATCH | `/api/v1/projects/{id}/status` | `PROJECT_WRITE` |  |

## Users, roles and shifts

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/permissions` | `ROLE_MANAGE` |  |
| GET | `/api/v1/roles` | `USER_READ` or `ROLE_MANAGE` |  |
| PUT | `/api/v1/roles/{code}/permissions` | `ROLE_MANAGE` |  |
| GET | `/api/v1/shifts` | authenticated |  |
| POST | `/api/v1/shifts` | `USER_WRITE` |  |
| PUT | `/api/v1/shifts/{id}` | `USER_WRITE` |  |
| GET | `/api/v1/users` | `USER_READ` |  |
| POST | `/api/v1/users` | `USER_WRITE` |  |
| GET | `/api/v1/users/directory` | authenticated |  |
| GET | `/api/v1/users/workload` | `USER_READ` |  |
| GET | `/api/v1/users/{id}` | `USER_READ` |  |
| PUT | `/api/v1/users/{id}` | `USER_WRITE` |  |
| POST | `/api/v1/users/{id}/reset-password` | `USER_WRITE` |  |
| POST | `/api/v1/users/{id}/unlock` | `USER_WRITE` |  |
| GET | `/api/v1/account-requests` | administrator with `USER_WRITE` | List pending public account requests |
| POST | `/api/v1/account-requests/{id}/approve` | administrator with `USER_WRITE` | Create an inactive account and email a one-time password setup link |
| POST | `/api/v1/account-requests/{id}/reject` | administrator with `USER_WRITE` | Reject a pending account request |

## Teams

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/teams` | `TEAM_READ` |  |
| POST | `/api/v1/teams` | `TEAM_WRITE` |  |
| DELETE | `/api/v1/teams/{id}` | `TEAM_WRITE` |  |
| GET | `/api/v1/teams/{id}` | `TEAM_READ` |  |
| PUT | `/api/v1/teams/{id}` | `TEAM_WRITE` |  |

## Tickets

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/tickets` | `TICKET_READ` |  |
| POST | `/api/v1/tickets` | `TICKET_CREATE` |  |
| GET | `/api/v1/tickets/{id}` | `TICKET_READ` |  |
| PUT | `/api/v1/tickets/{id}` | `TICKET_UPDATE` |  |
| POST | `/api/v1/tickets/{id}/assign` | `TICKET_ASSIGN` |  |
| POST | `/api/v1/tickets/{id}/attachments` | `TICKET_UPDATE` |  |
| GET | `/api/v1/tickets/{id}/attachments/{attachmentId}` | `TICKET_READ` |  |
| POST | `/api/v1/tickets/{id}/comments` | `TICKET_UPDATE` |  |
| POST | `/api/v1/tickets/{id}/escalate` | `TICKET_ESCALATE` |  |
| POST | `/api/v1/tickets/{id}/status` | `TICKET_UPDATE` |  |

## SLA

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/sla/calendars` | `SLA_READ` |  |
| POST | `/api/v1/sla/calendars` | `SLA_WRITE` |  |
| PUT | `/api/v1/sla/calendars/{id}` | `SLA_WRITE` |  |
| POST | `/api/v1/sla/calendars/{id}/holidays` | `SLA_WRITE` |  |
| GET | `/api/v1/sla/escalation-rules` | `SLA_READ` |  |
| POST | `/api/v1/sla/escalation-rules` | `SLA_WRITE` |  |
| DELETE | `/api/v1/sla/escalation-rules/{id}` | `SLA_WRITE` |  |
| DELETE | `/api/v1/sla/holidays/{id}` | `SLA_WRITE` |  |
| GET | `/api/v1/sla/policies` | `SLA_READ` |  |
| POST | `/api/v1/sla/policies` | `SLA_WRITE` |  |
| DELETE | `/api/v1/sla/policies/{id}` | `SLA_WRITE` |  |
| PUT | `/api/v1/sla/policies/{id}` | `SLA_WRITE` |  |

## Integrations

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/integrations` | `INTEGRATION_MANAGE` |  |
| POST | `/api/v1/integrations` | `INTEGRATION_MANAGE` | Create integration. Credentials are encrypted at rest and never returned. |
| GET | `/api/v1/integrations/connectors` | `INTEGRATION_MANAGE` |  |
| DELETE | `/api/v1/integrations/{id}` | `INTEGRATION_MANAGE` |  |
| PUT | `/api/v1/integrations/{id}` | `INTEGRATION_MANAGE` |  |
| GET | `/api/v1/integrations/{id}/logs` | `INTEGRATION_MANAGE` |  |
| POST | `/api/v1/integrations/{id}/sync` | `INTEGRATION_MANAGE` |  |
| POST | `/api/v1/integrations/{id}/test` | `INTEGRATION_MANAGE` |  |

## Workflow & Automation

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/automation/approvals` | authenticated |  |
| POST | `/api/v1/automation/approvals/{id}/decide` | authenticated |  |
| GET | `/api/v1/automation/executions` | `AUTOMATION_MANAGE` |  |
| GET | `/api/v1/automation/rules` | `AUTOMATION_MANAGE` |  |
| POST | `/api/v1/automation/rules` | `AUTOMATION_MANAGE` |  |
| DELETE | `/api/v1/automation/rules/{id}` | `AUTOMATION_MANAGE` |  |
| PUT | `/api/v1/automation/rules/{id}` | `AUTOMATION_MANAGE` |  |
| GET | `/api/v1/automation/triggers` | authenticated |  |

## Reports & Analytics

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/reports/{type}` | `REPORT_VIEW` | type: productivity, sla, pending, ageing, client, project, team, daily, weekly, monthly |
| GET | `/api/v1/reports/{type}/export` | `REPORT_EXPORT` |  |

## Notifications

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/notifications` | authenticated |  |
| GET | `/api/v1/notifications/email-log` | `NOTIFICATION_MANAGE` |  |
| POST | `/api/v1/notifications/email-log/{id}/retry` | `NOTIFICATION_MANAGE` |  |
| POST | `/api/v1/notifications/read-all` | authenticated |  |
| GET | `/api/v1/notifications/rules` | `NOTIFICATION_MANAGE` |  |
| POST | `/api/v1/notifications/rules` | `NOTIFICATION_MANAGE` |  |
| DELETE | `/api/v1/notifications/rules/{id}` | `NOTIFICATION_MANAGE` |  |
| GET | `/api/v1/notifications/unread-count` | authenticated |  |
| POST | `/api/v1/notifications/{id}/read` | authenticated |  |

## Knowledge Base & SOP

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/knowledge/articles` | `KB_READ` |  |
| POST | `/api/v1/knowledge/articles` | `KB_WRITE` |  |
| GET | `/api/v1/knowledge/articles/{id}` | `KB_READ` |  |
| PUT | `/api/v1/knowledge/articles/{id}` | `KB_WRITE` |  |
| POST | `/api/v1/knowledge/articles/{id}/helpful` | `KB_READ` |  |
| GET | `/api/v1/knowledge/categories` | `KB_READ` |  |
| POST | `/api/v1/knowledge/categories` | `KB_WRITE` |  |
| GET | `/api/v1/knowledge/usage` | `KB_READ` |  |

## Login Security & IP Activity Monitoring

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/security/active-sessions` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/anonymous-access` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/dashboard` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/failed-logins` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/ip-activity` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/ip-rules` | `SECURITY_VIEW` |  |
| POST | `/api/v1/security/ip-rules` | `SECURITY_MANAGE` |  |
| DELETE | `/api/v1/security/ip-rules/{id}` | `SECURITY_MANAGE` |  |
| PATCH | `/api/v1/security/ip-rules/{id}` | `SECURITY_MANAGE` |  |
| GET | `/api/v1/security/login-activity` | `SECURITY_VIEW` |  |
| GET | `/api/v1/security/reports/export` | `SECURITY_VIEW` and `REPORT_EXPORT` | Export login activity as CSV (type=logins/failed/anonymous/ip) |
| GET | `/api/v1/security/sessions/{id}/history` | `SECURITY_VIEW` |  |
| POST | `/api/v1/security/sessions/{id}/terminate` | `SECURITY_MANAGE` | Terminate an active session (revokes refresh tokens, closes attendance with source ADMIN_TERMINATED) |
| GET | `/api/v1/security/suspicious-activity` | `SECURITY_VIEW` |  |
| POST | `/api/v1/security/users/{userId}/terminate-sessions` | `SECURITY_MANAGE` |  |

## Audit

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/audit` | `AUDIT_VIEW` |  |

## System configuration

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/settings` | `SETTINGS_MANAGE` |  |
| GET | `/api/v1/settings/email-templates` | `SETTINGS_MANAGE` |  |
| PUT | `/api/v1/settings/email-templates/{code}` | `SETTINGS_MANAGE` |  |
| GET | `/api/v1/settings/jobs` | `SETTINGS_MANAGE` |  |
| PUT | `/api/v1/settings/priorities/{code}` | `SETTINGS_MANAGE` |  |
| GET | `/api/v1/settings/reference` | authenticated |  |
| PUT | `/api/v1/settings/statuses/{code}` | `SETTINGS_MANAGE` |  |
| PUT | `/api/v1/settings/{key}` | `SETTINGS_MANAGE` |  |

## Attendance & Break Monitoring

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/attendance/breaks/current` | `ATTENDANCE_SELF` |  |
| POST | `/api/v1/attendance/breaks/end` | `ATTENDANCE_SELF` |  |
| GET | `/api/v1/attendance/breaks/history` | `ATTENDANCE_SELF` |  |
| POST | `/api/v1/attendance/breaks/start` | `ATTENDANCE_SELF` |  |
| GET | `/api/v1/attendance/categories` | authenticated |  |
| GET | `/api/v1/attendance/corrections` | `ATTENDANCE_MANAGE` |  |
| POST | `/api/v1/attendance/corrections` | `ATTENDANCE_MANAGE` | Administrative correction. The original value is preserved in the correction record and audit log. |
| GET | `/api/v1/attendance/daily` | authenticated |  |
| GET | `/api/v1/attendance/events` | authenticated | Attendance history: login, logout, break start/end, idle start/end, confirmations, reminders, corrections |
| POST | `/api/v1/attendance/heartbeat` | `ATTENDANCE_SELF` | Browser heartbeat with last in-app activity time (used for disconnect and idle detection) |
| POST | `/api/v1/attendance/idle/confirm` | `ATTENDANCE_SELF` | Employee confirms status after an inactivity prompt: WORKING, ON_BREAK or AWAY |
| GET | `/api/v1/attendance/idle/events` | `ATTENDANCE_SELF` |  |
| POST | `/api/v1/attendance/idle/events` | `ATTENDANCE_SELF` | Report that the configured in-app inactivity threshold was reached (idempotent per session) |
| GET | `/api/v1/attendance/idle/status` | `ATTENDANCE_SELF` |  |
| POST | `/api/v1/attendance/login` | `ATTENDANCE_SELF` | Attendance check-in for the current login (idempotent). Attendance is recorded automatically at sign-in; this returns the active session. |
| POST | `/api/v1/attendance/logout` | `ATTENDANCE_SELF` | End the current attendance session and sign out |
| GET | `/api/v1/attendance/me` | `ATTENDANCE_SELF` | Today's attendance for the signed-in employee: login time, status, breaks, idle events, exceptions |
| GET | `/api/v1/attendance/monthly` | authenticated |  |
| GET | `/api/v1/attendance/monthly-reports` | authenticated |  |
| POST | `/api/v1/attendance/monthly-reports` | `ATTENDANCE_MANAGE` | Generate and send a monthly report for a completed month (fails with 409 if that period was already generated) |
| GET | `/api/v1/attendance/monthly-reports/{id}` | authenticated |  |
| GET | `/api/v1/attendance/monthly-reports/{id}/download` | authenticated |  |
| POST | `/api/v1/attendance/monthly-reports/{id}/send` | `ATTENDANCE_MANAGE` | Re-send a monthly report. Already-delivered reports require force=true (audited). |
| GET | `/api/v1/attendance/policies` | authenticated |  |
| POST | `/api/v1/attendance/policies` | `ATTENDANCE_MANAGE` |  |
| DELETE | `/api/v1/attendance/policies/{id}` | `ATTENDANCE_MANAGE` |  |
| PUT | `/api/v1/attendance/policies/{id}` | `ATTENDANCE_MANAGE` |  |
| GET | `/api/v1/attendance/reminders` | `ATTENDANCE_SELF` |  |
| GET | `/api/v1/attendance/reports/daily` | authenticated |  |
| GET | `/api/v1/attendance/reports/export` | authenticated | Export attendance report. type=daily/monthly, format=csv/xlsx/pdf |
| GET | `/api/v1/attendance/reports/monthly` | authenticated |  |
| GET | `/api/v1/attendance/sessions` | `ATTENDANCE_SELF` |  |
| GET | `/api/v1/attendance/settings` | authenticated |  |
| PUT | `/api/v1/attendance/settings` | `ATTENDANCE_MANAGE` |  |
| GET | `/api/v1/attendance/settings/effective` | authenticated |  |
| DELETE | `/api/v1/attendance/settings/projects/{projectId}` | `ATTENDANCE_MANAGE` |  |
| PUT | `/api/v1/attendance/settings/projects/{projectId}` | `ATTENDANCE_MANAGE` |  |
| GET | `/api/v1/attendance/summary` | authenticated | Attendance dashboard (scoped): logged in, active, on break, idle, missing logouts, compliance, live status |
