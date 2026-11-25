# Deployment guide

## Configuration reference (environment variables)

| Variable | Purpose | Default (dev) |
|---|---|---|
| `SUMA_DB_URL` / `SUMA_DB_USER` / `SUMA_DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/suma`, `suma`, `suma` |
| `SUMA_JWT_SECRET` | HMAC key for access tokens, **≥ 32 bytes, required in production** | insecure dev value |
| `SUMA_ACCESS_TTL_MINUTES` / `SUMA_REFRESH_TTL_HOURS` | Access token and session lifetime | 15 / 14 |
| `SUMA_ENCRYPTION_KEY` | Key for integration credentials (AES-256-GCM). Rotating it makes stored credentials unreadable. | dev value |
| `SUMA_ADMIN_USERNAME` / `SUMA_ADMIN_EMAIL` / `SUMA_ADMIN_PASSWORD` | Bootstrap administrator, created only when missing | `admin` / `admin@suma.local` / `Admin@12345` |
| `SUMA_TRUSTED_PROXIES` | Comma-separated CIDRs whose `X-Forwarded-For` is honoured. Leave empty when clients connect directly. | empty |
| `SUMA_CORS_ORIGINS` | Allowed browser origins | `http://localhost:5173,http://localhost:4173` |
| `SUMA_COOKIE_SECURE` | Mark the refresh cookie `Secure` (set `true` behind HTTPS) | `false` |
| `SUMA_SMTP_HOST` / `PORT` / `USER` / `PASSWORD` / `AUTH` / `STARTTLS`, `SUMA_MAIL_FROM` | Outgoing email | `localhost:1025` (Mailpit) |
| `SUMA_SCHEDULING_ENABLED` | Run background jobs on this node | `true` |
| `SUMA_LOGIN_RATE_MAX` / `SUMA_LOGIN_RATE_WINDOW` | Login attempts per IP per window (seconds) | 20 / 300 |
| `SUMA_UPLOADS_DIR` | Attachment storage path (persistent volume) | `./uploads` |
| `SUMA_API_DOCS_ENABLED` | Serve Swagger UI and OpenAPI JSON (set `false` in production) | `true` |
| `SUMA_DEMO_DATA` | Seed demonstration data — **never in production** | `false` |

## Production checklist

1. Generate fresh `SUMA_JWT_SECRET` and `SUMA_ENCRYPTION_KEY`; keep them in a secret manager, not in the image.
2. Set `SUMA_ADMIN_PASSWORD`, sign in, change it again from Profile.
3. Serve over HTTPS and set `SUMA_COOKIE_SECURE=true`.
4. Configure `SUMA_TRUSTED_PROXIES` to exactly your load balancer / ingress ranges. The bundled nginx overwrites `X-Forwarded-For` with the client address; if another proxy sits in front of it, switch nginx to `$proxy_add_x_forwarded_for` and trust both hops.
5. Point SMTP at your relay and verify each project's Project Lead email in the UI before the 1st of the month.
6. Set the attendance reporting timezone and policy thresholds (Configuration → Attendance). Break limits are intentionally empty until your organisation defines them.
7. Back up the PostgreSQL volume and the uploads volume.
8. Set `SUMA_API_DOCS_ENABLED=false`. With `SUMA_COOKIE_SECURE=true` the backend refuses to start if the JWT secret or encryption key are still the development defaults.
9. Running several backend instances is supported: ShedLock makes each scheduled job run on one node at a time. The session-validation cache is per node (20 s), so a terminated session can survive up to 20 s on other nodes.

## Scheduled jobs

| Job | Schedule | Purpose |
|---|---|---|
| attendance-reconciliation | every 5 min | close expired sessions; close sessions without heartbeat (missing logout, closed at last heartbeat) |
| idle-processing | every 2 min | server-side idle detection from heartbeats |
| break-reminders | every minute | reminders, limit-exceeded alerts, follow-ups (once per sequence) |
| daily-attendance-summary | hourly :20 | persist yesterday's summaries, notify exceptions; month summary at month end |
| monthly-attendance-report | hourly :05 | on/after the 1st at the configured hour, generate last month's report per project (idempotent) |
| email-delivery | every minute | deliver queued email with exponential backoff |
| sla-breach-detection | every minute | response/resolution breaches, percentage escalations |
| ticket-pending-reminders | hourly :40 | pending-ticket reminders, auto-close resolved tickets |
| integration-sync | every minute | run enabled integrations whose interval elapsed |
| retention-cleanup | daily 03:15 UTC | apply retention settings |

Execution history: Configuration → Scheduled jobs (`job_execution_log`).
