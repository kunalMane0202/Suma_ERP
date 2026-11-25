# SUMA Tracking Tool — Database schema

PostgreSQL 16. The schema is owned by **Flyway** migrations in `backend/src/main/resources/db/migration` (`V1`–`V7`); Hibernate only validates it (`ddl-auto: validate`). This document is generated from the live schema and annotated.

## Conventions

- Primary keys are `UUID`. Timestamps are `timestamptz` and stored in **UTC**; attendance "work dates" are calculated in the configured reporting timezone.
- Mutable business tables carry `created_at`, `updated_at` and an optimistic-locking `version` column.
- Status-like columns are `varchar` with `CHECK` constraints (enumerations in code).
- No passwords or tokens are stored in clear text: `users.password_hash` is BCrypt (cost 12); refresh tokens are stored as SHA-256 hashes; integration credentials are AES-256-GCM encrypted (`integrations.credentials_encrypted`).

## Key relationships

```
users ─< user_sessions ─< refresh_tokens
users ─< attendance_sessions (1 per user_session, UNIQUE auth_session_id) ─< break_records
                                                                          ─< idle_events
users ─< attendance_events (immutable history: login, logout, breaks, idle, confirmations, reminders, corrections)
projects ─ attendance_settings (optional project override; one global row with project_id NULL)
projects ─ project_lead_id / project_lead_email (+ verified flag) ; project_members.designation = PROJECT_LEAD
users ─< attendance_daily_summary (UNIQUE user_id, work_date)
projects ─< attendance_report_logs (UNIQUE project_id, report_type, year, month, day → idempotent monthly delivery)
users ─< login_activity_logs ; ip_address ─< ip_activity_logs / anonymous_access_logs
clients ─< projects ─< tickets ─< ticket_comments / ticket_attachments / ticket_history
```

## Integrity rules that prevent duplicates

| Rule | Enforced by |
|---|---|
| One attendance session per authenticated login | `attendance_sessions.auth_session_id UNIQUE` |
| At most one active break per employee | partial unique index `uq_break_one_active_per_user (user_id) WHERE status='ACTIVE'` |
| At most one open idle event per session | partial unique index `uq_idle_one_open_per_session (session_id) WHERE ended_at IS NULL` |
| Monthly report generated/sent once per project and month | `attendance_report_logs UNIQUE (project_id, report_type, period_year, period_month, period_day)` |
| Break reminder sent once per sequence | `break_reminders UNIQUE (break_id, reminder_type, sequence_no)` |
| External tickets ingested once | partial unique index `uq_tickets_external (external_system, external_id)` |
| Daily summary row per employee/day | `attendance_daily_summary UNIQUE (user_id, work_date)` |
| Single global attendance settings row | partial unique index on `(project_id IS NULL)` |
| Scheduled jobs never run concurrently | ShedLock table `shedlock` |

## Retention

| Data | Setting | Default |
|---|---|---|
| Attendance events | `attendance_settings.retention_days` | 730 days |
| Login / anonymous / IP activity logs | `system_settings retention.security_log_days` | 365 days |
| Audit logs | `retention.audit_log_days` | 1095 days |
| Notifications | `retention.notification_days` | 180 days |

Cleanup runs daily (`retention-cleanup` job, 03:15 UTC).

## Identity, sessions and audit

### `roles`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `code` | character varying(40) | no |  |
| `name` | character varying(100) | no |  |
| `description` | character varying(500) | yes |  |
| `system_role` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (code)`; `btree (id)`

### `permissions`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `code` | character varying(60) | no |  |
| `module` | character varying(40) | no |  |
| `description` | character varying(300) | yes |  |

Indexes: `btree (code)`; `btree (id)`

### `role_permissions`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `role_id` | uuid | no | → `roles` |
| `permission_id` | uuid | no | → `permissions` |

Indexes: `btree (role_id, permission_id)`

### `users`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `employee_code` | character varying(40) | no |  |
| `username` | character varying(80) | no |  |
| `email` | character varying(200) | no |  |
| `password_hash` | character varying(100) | no |  |
| `full_name` | character varying(150) | no |  |
| `phone` | character varying(40) | yes |  |
| `job_title` | character varying(100) | yes |  |
| `role_id` | uuid | no | → `roles` |
| `shift_id` | uuid | yes | → `shifts` |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `theme_preference` | character varying(10) | no | 'LIGHT'::character varying |
| `timezone` | character varying(60) | yes |  |
| `max_open_tickets` | integer | yes |  |
| `failed_login_count` | integer | no | 0 |
| `locked_until` | timestamp with time zone | yes |  |
| `last_login_at` | timestamp with time zone | yes |  |
| `last_login_ip` | character varying(45) | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |
| `version` | bigint | no | 0 |

Indexes: `btree (role_id)`; `btree (status)`; `btree (email)`; `btree (employee_code)`; `btree (id)`; `btree (username)`

### `user_weekly_offs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `user_id` | uuid | no | → `users` |
| `day_of_week` | smallint | no |  |

Indexes: `btree (user_id, day_of_week)`

### `shifts`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(100) | no |  |
| `start_time` | time without time zone | no |  |
| `end_time` | time without time zone | no |  |
| `timezone` | character varying(60) | no | 'UTC'::character varying |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (name)`; `btree (id)`

### `user_sessions`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `auth_method` | character varying(30) | no | 'PASSWORD'::character varying |
| `ip_address` | character varying(45) | yes |  |
| `ip_version` | smallint | yes |  |
| `last_ip` | character varying(45) | yes |  |
| `user_agent` | character varying(500) | yes |  |
| `device` | character varying(60) | yes |  |
| `browser` | character varying(60) | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `last_seen_at` | timestamp with time zone | no | now() |
| `expires_at` | timestamp with time zone | no |  |
| `ended_at` | timestamp with time zone | yes |  |
| `ended_reason` | character varying(200) | yes |  |
| `ended_by` | uuid | yes | → `users` |

Indexes: `btree (status) WHERE ((status)::text = 'ACTIVE'::text)`; `btree (user_id, created_at DESC)`; `btree (id)`

### `refresh_tokens`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `session_id` | uuid | no | → `user_sessions` |
| `user_id` | uuid | no | → `users` |
| `token_hash` | character varying(100) | no |  |
| `issued_at` | timestamp with time zone | no | now() |
| `expires_at` | timestamp with time zone | no |  |
| `used_at` | timestamp with time zone | yes |  |
| `revoked_at` | timestamp with time zone | yes |  |

Indexes: `btree (session_id)`; `btree (id)`; `btree (token_hash)`

### `audit_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `actor_id` | uuid | yes | → `users` |
| `actor_username` | character varying(80) | yes |  |
| `action` | character varying(80) | no |  |
| `entity_type` | character varying(60) | yes |  |
| `entity_id` | character varying(60) | yes |  |
| `details` | text | yes |  |
| `ip_address` | character varying(45) | yes |  |
| `correlation_id` | character varying(64) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (actor_id)`; `btree (created_at DESC)`; `btree (entity_type, entity_id)`

### `shedlock`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `name` | character varying(64) | no |  |
| `lock_until` | timestamp with time zone | no |  |
| `locked_at` | timestamp with time zone | no |  |
| `locked_by` | character varying(255) | no |  |

Indexes: `btree (name)`

### `job_execution_log`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `job_name` | character varying(80) | no |  |
| `started_at` | timestamp with time zone | no |  |
| `finished_at` | timestamp with time zone | yes |  |
| `status` | character varying(20) | no |  |
| `items_processed` | integer | no | 0 |
| `message` | text | yes |  |

Indexes: `btree (job_name, started_at DESC)`; `btree (id)`

## Organisation

### `clients`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `code` | character varying(30) | no |  |
| `name` | character varying(200) | no |  |
| `industry` | character varying(100) | yes |  |
| `email` | character varying(200) | yes |  |
| `phone` | character varying(40) | yes |  |
| `address` | character varying(500) | yes |  |
| `website` | character varying(200) | yes |  |
| `account_manager_id` | uuid | yes | → `users` |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `configuration` | text | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |
| `version` | bigint | no | 0 |

Indexes: `btree (code)`; `btree (id)`; `btree (status)`

### `client_contacts`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `client_id` | uuid | no | → `clients` |
| `name` | character varying(150) | no |  |
| `email` | character varying(200) | yes |  |
| `phone` | character varying(40) | yes |  |
| `role_title` | character varying(100) | yes |  |
| `is_primary` | boolean | no | false |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (client_id)`

### `projects`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `client_id` | uuid | no | → `clients` |
| `code` | character varying(30) | no |  |
| `name` | character varying(200) | no |  |
| `description` | text | yes |  |
| `manager_id` | uuid | yes | → `users` |
| `team_leader_id` | uuid | yes | → `users` |
| `project_lead_id` | uuid | yes | → `users` |
| `project_lead_email` | character varying(200) | yes |  |
| `project_lead_email_verified` | boolean | no | false |
| `calendar_id` | uuid | yes | → `business_calendars` |
| `support_hours` | character varying(100) | yes |  |
| `timezone` | character varying(60) | no | 'UTC'::character varying |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `start_date` | date | yes |  |
| `end_date` | date | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |
| `version` | bigint | no | 0 |

Indexes: `btree (client_id)`; `btree (status)`; `btree (code)`; `btree (id)`

### `project_members`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `project_id` | uuid | no | → `projects` |
| `user_id` | uuid | no | → `users` |
| `designation` | character varying(30) | no | 'MEMBER'::character varying |
| `allocation_percent` | integer | no | 100 |
| `is_primary` | boolean | no | true |
| `assigned_at` | timestamp with time zone | no | now() |

Indexes: `btree (user_id)`; `btree (project_id, user_id)`

### `project_shifts`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `project_id` | uuid | no | → `projects` |
| `shift_id` | uuid | no | → `shifts` |

Indexes: `btree (project_id, shift_id)`

### `teams`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(120) | no |  |
| `description` | character varying(500) | yes |  |
| `project_id` | uuid | yes | → `projects` |
| `team_leader_id` | uuid | yes | → `users` |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (project_id)`; `btree (name)`; `btree (id)`

### `team_members`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `team_id` | uuid | no | → `teams` |
| `user_id` | uuid | no | → `users` |
| `joined_at` | timestamp with time zone | no | now() |

Indexes: `btree (user_id)`; `btree (team_id, user_id)`

### `ticket_categories`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `project_id` | uuid | yes | → `projects` |
| `parent_id` | uuid | yes | → `ticket_categories` |
| `name` | character varying(120) | no |  |
| `keywords` | character varying(500) | yes |  |
| `default_team_id` | uuid | yes | → `teams` |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (project_id)`; `btree (id)`

## Tickets and SLA

### `tickets`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ticket_number` | character varying(30) | no |  |
| `project_id` | uuid | no | → `projects` |
| `client_id` | uuid | no | → `clients` |
| `title` | character varying(300) | no |  |
| `description` | text | yes |  |
| `status` | character varying(20) | no |  |
| `priority` | character varying(20) | no |  |
| `category_id` | uuid | yes | → `ticket_categories` |
| `subcategory_id` | uuid | yes | → `ticket_categories` |
| `source` | character varying(20) | no | 'MANUAL'::character varying |
| `external_system` | character varying(40) | yes |  |
| `external_id` | character varying(120) | yes |  |
| `reporter_name` | character varying(150) | yes |  |
| `reporter_email` | character varying(200) | yes |  |
| `created_by` | uuid | yes | → `users` |
| `assignee_id` | uuid | yes | → `users` |
| `team_id` | uuid | yes | → `teams` |
| `escalation_level` | integer | no | 0 |
| `reopened_count` | integer | no | 0 |
| `first_response_at` | timestamp with time zone | yes |  |
| `resolved_at` | timestamp with time zone | yes |  |
| `closed_at` | timestamp with time zone | yes |  |
| `response_due_at` | timestamp with time zone | yes |  |
| `resolution_due_at` | timestamp with time zone | yes |  |
| `sla_paused_at` | timestamp with time zone | yes |  |
| `sla_paused_seconds` | bigint | no | 0 |
| `response_breached` | boolean | no | false |
| `resolution_breached` | boolean | no | false |
| `sla_policy_id` | uuid | yes |  |
| `custom_fields` | text | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |
| `version` | bigint | no | 0 |

Indexes: `btree (assignee_id, status)`; `btree (client_id)`; `btree (created_at DESC)`; `btree (project_id, status)`; `btree (id)`; `btree (ticket_number)`; `btree (external_system, external_id) WHERE (external_id IS NOT NULL)`

### `ticket_comments`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ticket_id` | uuid | no | → `tickets` |
| `author_id` | uuid | yes | → `users` |
| `body` | text | no |  |
| `internal` | boolean | no | false |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (ticket_id, created_at)`; `btree (id)`

### `ticket_attachments`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ticket_id` | uuid | no | → `tickets` |
| `uploaded_by` | uuid | yes | → `users` |
| `file_name` | character varying(255) | no |  |
| `content_type` | character varying(120) | no |  |
| `size_bytes` | bigint | no |  |
| `storage_path` | character varying(500) | no |  |
| `sha256` | character varying(64) | no |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (ticket_id)`; `btree (id)`

### `ticket_history`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ticket_id` | uuid | no | → `tickets` |
| `actor_id` | uuid | yes | → `users` |
| `actor_name` | character varying(150) | yes |  |
| `action` | character varying(40) | no |  |
| `field` | character varying(60) | yes |  |
| `old_value` | character varying(500) | yes |  |
| `new_value` | character varying(500) | yes |  |
| `note` | text | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (ticket_id, created_at)`; `btree (id)`

### `ticket_status_config`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `code` | character varying(20) | no |  |
| `label` | character varying(60) | no |  |
| `color` | character varying(20) | no |  |
| `sort_order` | integer | no |  |
| `is_terminal` | boolean | no | false |

Indexes: `btree (code)`

### `ticket_priority_config`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `code` | character varying(20) | no |  |
| `label` | character varying(60) | no |  |
| `color` | character varying(20) | no |  |
| `sort_order` | integer | no |  |

Indexes: `btree (code)`

### `sla_policies`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(120) | no |  |
| `project_id` | uuid | yes | → `projects` |
| `priority` | character varying(20) | no |  |
| `response_minutes` | integer | no |  |
| `resolution_minutes` | integer | no |  |
| `calendar_id` | uuid | yes | → `business_calendars` |
| `business_hours_only` | boolean | no | true |
| `pause_on_pending` | boolean | no | true |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (COALESCE(project_id, '00000000-0000-0000-0000-000000000000'::uuid), priority) WHERE active`

### `business_calendars`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(100) | no |  |
| `timezone` | character varying(60) | no | 'UTC'::character varying |
| `is_default` | boolean | no | false |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (name)`; `btree (id)`

### `business_hours`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `calendar_id` | uuid | no | → `business_calendars` |
| `day_of_week` | smallint | no |  |
| `start_time` | time without time zone | no |  |
| `end_time` | time without time zone | no |  |

Indexes: `btree (calendar_id, day_of_week)`; `btree (id)`

### `holidays`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `calendar_id` | uuid | no | → `business_calendars` |
| `holiday_date` | date | no |  |
| `name` | character varying(150) | no |  |

Indexes: `btree (calendar_id, holiday_date)`; `btree (id)`

### `escalation_rules`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `project_id` | uuid | yes | → `projects` |
| `priority` | character varying(20) | yes |  |
| `trigger_type` | character varying(30) | no |  |
| `threshold_percent` | integer | yes |  |
| `escalation_level` | integer | no |  |
| `notify_role` | character varying(40) | yes |  |
| `notify_user_id` | uuid | yes | → `users` |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`

## Attendance and break monitoring

### `attendance_settings`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `project_id` | uuid | yes | → `projects` |
| `default_work_hours` | numeric | yes |  |
| `idle_threshold_minutes` | integer | no | 20 |
| `heartbeat_timeout_minutes` | integer | no | 30 |
| `missing_logout_after_hours` | integer | no | 16 |
| `max_break_minutes` | integer | yes |  |
| `max_daily_break_minutes` | integer | yes |  |
| `max_breaks_per_day` | integer | yes |  |
| `break_reminder_interval_minutes` | integer | yes |  |
| `reporting_timezone` | character varying(60) | no | 'UTC'::character varying |
| `notify_team_leader_on_idle` | boolean | no | false |
| `monthly_report_enabled` | boolean | no | true |
| `monthly_report_hour` | integer | no | 6 |
| `email_max_attempts` | integer | no | 5 |
| `email_retry_backoff_minutes` | integer | no | 15 |
| `retention_days` | integer | no | 730 |
| `updated_by` | uuid | yes | → `users` |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (project_id)`; `btree (((project_id IS NULL))) WHERE (project_id IS NULL)`

### `break_categories`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `code` | character varying(30) | no |  |
| `label` | character varying(80) | no |  |
| `active` | boolean | no | true |
| `sort_order` | integer | no | 0 |

Indexes: `btree (code)`

### `break_policies`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(120) | no |  |
| `project_id` | uuid | yes | → `projects` |
| `break_category` | character varying(30) | yes | → `break_categories` |
| `max_duration_minutes` | integer | yes |  |
| `max_per_day` | integer | yes |  |
| `reminder_after_minutes` | integer | yes |  |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`

### `attendance_sessions`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `auth_session_id` | uuid | no | → `user_sessions` |
| `project_id` | uuid | yes | → `projects` |
| `team_id` | uuid | yes | → `teams` |
| `work_date` | date | no |  |
| `login_at` | timestamp with time zone | no |  |
| `logout_at` | timestamp with time zone | yes |  |
| `logout_source` | character varying(30) | yes |  |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `current_state` | character varying(20) | no | 'WORKING'::character varying |
| `last_heartbeat_at` | timestamp with time zone | yes |  |
| `last_activity_at` | timestamp with time zone | yes |  |
| `login_ip` | character varying(45) | yes |  |
| `user_agent` | character varying(500) | yes |  |
| `device` | character varying(60) | yes |  |
| `browser` | character varying(60) | yes |  |
| `duration_seconds` | bigint | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (auth_session_id)`; `btree (id)`; `btree (status) WHERE ((status)::text = 'ACTIVE'::text)`; `btree (work_date)`; `btree (project_id, work_date)`; `btree (user_id, work_date)`

### `attendance_events`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `session_id` | uuid | yes | → `attendance_sessions` |
| `event_type` | character varying(30) | no |  |
| `occurred_at` | timestamp with time zone | no |  |
| `source` | character varying(30) | no |  |
| `details` | text | yes |  |
| `created_by` | uuid | yes | → `users` |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (event_type, occurred_at DESC)`; `btree (user_id, occurred_at DESC)`

### `break_records`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `session_id` | uuid | no | → `attendance_sessions` |
| `project_id` | uuid | yes | → `projects` |
| `break_category` | character varying(30) | no | → `break_categories` |
| `work_date` | date | no |  |
| `started_at` | timestamp with time zone | no |  |
| `ended_at` | timestamp with time zone | yes |  |
| `duration_seconds` | bigint | yes |  |
| `status` | character varying(20) | no | 'ACTIVE'::character varying |
| `start_source` | character varying(30) | no |  |
| `end_source` | character varying(30) | yes |  |
| `exceeded` | boolean | no | false |
| `notes` | character varying(500) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (session_id)`; `btree (user_id, work_date)`; `btree (user_id) WHERE ((status)::text = 'ACTIVE'::text)`

### `break_reminders`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `break_id` | uuid | no | → `break_records` |
| `user_id` | uuid | no | → `users` |
| `reminder_type` | character varying(20) | no |  |
| `sequence_no` | integer | no | 1 |
| `channel` | character varying(20) | no | 'IN_APP'::character varying |
| `sent_at` | timestamp with time zone | no | now() |
| `acknowledged_at` | timestamp with time zone | yes |  |

Indexes: `btree (break_id, reminder_type, sequence_no)`; `btree (id)`

### `idle_events`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `session_id` | uuid | no | → `attendance_sessions` |
| `work_date` | date | no |  |
| `idle_started_at` | timestamp with time zone | no |  |
| `detected_at` | timestamp with time zone | no |  |
| `ended_at` | timestamp with time zone | yes |  |
| `duration_seconds` | bigint | yes |  |
| `status` | character varying(20) | no | 'UNCONFIRMED'::character varying |
| `response` | character varying(20) | yes |  |
| `responded_at` | timestamp with time zone | yes |  |
| `source` | character varying(30) | no | 'BROWSER'::character varying |
| `team_leader_notified` | boolean | no | false |
| `linked_break_id` | uuid | yes | → `break_records` |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (user_id, work_date)`; `btree (session_id) WHERE (ended_at IS NULL)`

### `attendance_corrections`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `target_type` | character varying(20) | no |  |
| `target_id` | uuid | no |  |
| `field` | character varying(40) | no |  |
| `original_value` | character varying(100) | yes |  |
| `corrected_value` | character varying(100) | yes |  |
| `reason` | character varying(500) | no |  |
| `corrected_by` | uuid | no | → `users` |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (target_type, target_id)`

### `attendance_daily_summary`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `work_date` | date | no |  |
| `project_id` | uuid | yes | → `projects` |
| `team_id` | uuid | yes | → `teams` |
| `first_login_at` | timestamp with time zone | yes |  |
| `last_logout_at` | timestamp with time zone | yes |  |
| `session_count` | integer | no | 0 |
| `session_seconds` | bigint | no | 0 |
| `break_count` | integer | no | 0 |
| `break_seconds` | bigint | no | 0 |
| `idle_count` | integer | no | 0 |
| `idle_seconds` | bigint | no | 0 |
| `exceeded_break_count` | integer | no | 0 |
| `missing_logout` | boolean | no | false |
| `exceptions` | text | yes |  |
| `computed_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (user_id, work_date)`; `btree (work_date)`

### `attendance_monthly_summary`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `project_id` | uuid | yes | → `projects` |
| `period_year` | integer | no |  |
| `period_month` | integer | no |  |
| `days_present` | integer | no | 0 |
| `session_seconds` | bigint | no | 0 |
| `break_count` | integer | no | 0 |
| `break_seconds` | bigint | no | 0 |
| `idle_count` | integer | no | 0 |
| `idle_seconds` | bigint | no | 0 |
| `exception_count` | integer | no | 0 |
| `missing_logout_count` | integer | no | 0 |
| `computed_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (user_id, period_year, period_month)`

### `attendance_report_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `project_id` | uuid | no | → `projects` |
| `report_type` | character varying(20) | no |  |
| `period_year` | integer | no |  |
| `period_month` | integer | no |  |
| `period_day` | integer | no | 0 |
| `recipient_email` | character varying(200) | yes |  |
| `status` | character varying(30) | no |  |
| `attempts` | integer | no | 0 |
| `employee_count` | integer | no | 0 |
| `last_attempt_at` | timestamp with time zone | yes |  |
| `next_retry_at` | timestamp with time zone | yes |  |
| `last_error` | text | yes |  |
| `generated_at` | timestamp with time zone | yes |  |
| `sent_at` | timestamp with time zone | yes |  |
| `file_name` | character varying(200) | yes |  |
| `triggered_by` | character varying(20) | no | 'SCHEDULER'::character varying |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (project_id, report_type, period_year, period_month, period_day)`; `btree (status, next_retry_at)`

## Login security and IP monitoring

### `login_activity_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | yes | → `users` |
| `username_attempted` | character varying(120) | yes |  |
| `success` | boolean | no |  |
| `failure_category` | character varying(30) | yes |  |
| `ip_address` | character varying(45) | no |  |
| `ip_version` | smallint | no |  |
| `session_id` | uuid | yes | → `user_sessions` |
| `user_agent` | character varying(500) | yes |  |
| `device` | character varying(60) | yes |  |
| `browser` | character varying(60) | yes |  |
| `auth_method` | character varying(30) | no | 'PASSWORD'::character varying |
| `attempt_count` | integer | no | 1 |
| `suspicious` | boolean | no | false |
| `suspicious_reasons` | character varying(500) | yes |  |
| `geo_location` | character varying(120) | yes |  |
| `correlation_id` | character varying(64) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (created_at DESC)`; `btree (success, created_at DESC)`; `btree (ip_address, created_at DESC)`; `btree (user_id, created_at DESC)`; `btree (id)`

### `anonymous_access_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ip_address` | character varying(45) | no |  |
| `ip_version` | smallint | no |  |
| `http_method` | character varying(10) | no |  |
| `endpoint` | character varying(500) | no |  |
| `category` | character varying(30) | no |  |
| `status_code` | integer | no |  |
| `user_id` | uuid | yes | → `users` |
| `username` | character varying(120) | yes |  |
| `user_agent` | character varying(500) | yes |  |
| `correlation_id` | character varying(64) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (created_at DESC)`; `btree (ip_address, created_at DESC)`

### `ip_activity_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ip_address` | character varying(45) | no |  |
| `ip_version` | smallint | no |  |
| `user_id` | uuid | yes | → `users` |
| `activity_type` | character varying(30) | no |  |
| `details` | character varying(500) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (ip_address, created_at DESC)`; `btree (activity_type, created_at DESC)`; `btree (user_id, created_at DESC)`; `btree (id)`

### `trusted_ip_rules`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `cidr` | character varying(60) | no |  |
| `rule_type` | character varying(10) | no |  |
| `description` | character varying(300) | yes |  |
| `active` | boolean | no | true |
| `expires_at` | timestamp with time zone | yes |  |
| `created_by` | uuid | yes | → `users` |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (cidr, rule_type)`; `btree (id)`

### `session_activity_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `session_id` | uuid | no | → `user_sessions` |
| `user_id` | uuid | no | → `users` |
| `action` | character varying(30) | no |  |
| `ip_address` | character varying(45) | yes |  |
| `actor_id` | uuid | yes | → `users` |
| `reason` | character varying(300) | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (session_id, created_at)`; `btree (id)`

## Integrations, automation, notifications, knowledge, configuration

### `integrations`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(120) | no |  |
| `connector_type` | character varying(20) | no |  |
| `project_id` | uuid | no | → `projects` |
| `base_url` | character varying(500) | yes |  |
| `auth_type` | character varying(20) | no | 'NONE'::character varying |
| `credentials_encrypted` | text | yes |  |
| `query` | character varying(1000) | yes |  |
| `field_mapping` | text | yes |  |
| `sync_interval_minutes` | integer | no | 15 |
| `max_retries` | integer | no | 3 |
| `enabled` | boolean | no | false |
| `last_sync_at` | timestamp with time zone | yes |  |
| `last_status` | character varying(20) | yes |  |
| `consecutive_failures` | integer | no | 0 |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (name)`; `btree (id)`

### `integration_sync_logs`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `integration_id` | uuid | no | → `integrations` |
| `started_at` | timestamp with time zone | no |  |
| `finished_at` | timestamp with time zone | yes |  |
| `status` | character varying(20) | no |  |
| `attempt` | integer | no | 1 |
| `fetched` | integer | no | 0 |
| `created` | integer | no | 0 |
| `duplicates` | integer | no | 0 |
| `failed` | integer | no | 0 |
| `error` | text | yes |  |

Indexes: `btree (integration_id, started_at DESC)`; `btree (id)`

### `automation_rules`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(150) | no |  |
| `description` | character varying(500) | yes |  |
| `trigger_event` | character varying(40) | no |  |
| `conditions` | text | no | '[]'::text |
| `actions` | text | no | '[]'::text |
| `project_id` | uuid | yes | → `projects` |
| `priority` | integer | no | 100 |
| `stop_processing` | boolean | no | false |
| `active` | boolean | no | true |
| `created_by` | uuid | yes | → `users` |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (trigger_event, active)`

### `automation_executions`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `rule_id` | uuid | no | → `automation_rules` |
| `trigger_event` | character varying(40) | no |  |
| `entity_type` | character varying(40) | yes |  |
| `entity_id` | character varying(60) | yes |  |
| `status` | character varying(20) | no |  |
| `actions_run` | text | yes |  |
| `error` | text | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`; `btree (rule_id, created_at DESC)`

### `approval_requests`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `ticket_id` | uuid | no | → `tickets` |
| `requested_by` | uuid | yes | → `users` |
| `approver_id` | uuid | yes | → `users` |
| `status` | character varying(20) | no | 'PENDING'::character varying |
| `reason` | character varying(500) | yes |  |
| `decision_note` | character varying(500) | yes |  |
| `decided_at` | timestamp with time zone | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`

### `notifications`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `user_id` | uuid | no | → `users` |
| `type` | character varying(40) | no |  |
| `title` | character varying(200) | no |  |
| `message` | character varying(1000) | yes |  |
| `severity` | character varying(10) | no | 'INFO'::character varying |
| `entity_type` | character varying(40) | yes |  |
| `entity_id` | character varying(60) | yes |  |
| `read_at` | timestamp with time zone | yes |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (user_id) WHERE (read_at IS NULL)`; `btree (user_id, created_at DESC)`; `btree (id)`

### `email_outbox`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `recipient` | character varying(200) | no |  |
| `subject` | character varying(300) | no |  |
| `body` | text | no |  |
| `attachment_name` | character varying(200) | yes |  |
| `attachment_type` | character varying(120) | yes |  |
| `attachment_content` | bytea | yes |  |
| `status` | character varying(20) | no | 'PENDING'::character varying |
| `attempts` | integer | no | 0 |
| `max_attempts` | integer | no | 5 |
| `next_attempt_at` | timestamp with time zone | no | now() |
| `last_error` | text | yes |  |
| `related_type` | character varying(40) | yes |  |
| `related_id` | character varying(60) | yes |  |
| `created_at` | timestamp with time zone | no | now() |
| `sent_at` | timestamp with time zone | yes |  |

Indexes: `btree (id)`; `btree (status, next_attempt_at)`

### `notification_rules`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `project_id` | uuid | yes | → `projects` |
| `event_type` | character varying(40) | no |  |
| `channel` | character varying(20) | no |  |
| `recipient_role` | character varying(40) | yes |  |
| `escalation_level` | integer | no | 0 |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (id)`

### `email_templates`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `code` | character varying(60) | no |  |
| `subject` | character varying(300) | no |  |
| `body` | text | no |  |
| `description` | character varying(300) | yes |  |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (code)`

### `kb_categories`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `name` | character varying(120) | no |  |
| `parent_id` | uuid | yes | → `kb_categories` |

Indexes: `btree (name)`; `btree (id)`

### `kb_articles`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `title` | character varying(250) | no |  |
| `slug` | character varying(260) | no |  |
| `summary` | character varying(500) | yes |  |
| `content` | text | no |  |
| `article_type` | character varying(20) | no |  |
| `category_id` | uuid | yes | → `kb_categories` |
| `project_id` | uuid | yes | → `projects` |
| `tags` | character varying(500) | yes |  |
| `status` | character varying(20) | no | 'DRAFT'::character varying |
| `author_id` | uuid | yes | → `users` |
| `view_count` | integer | no | 0 |
| `helpful_count` | integer | no | 0 |
| `created_at` | timestamp with time zone | no | now() |
| `updated_at` | timestamp with time zone | no | now() |
| `version` | bigint | no | 0 |
| `search_vector` | tsvector | yes |  |

Indexes: `btree (project_id)`; `gin (search_vector)`; `btree (id)`; `btree (slug)`

### `kb_article_usage`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `article_id` | uuid | no | → `kb_articles` |
| `user_id` | uuid | yes | → `users` |
| `ticket_id` | uuid | yes | → `tickets` |
| `action` | character varying(20) | no |  |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (article_id, created_at DESC)`; `btree (id)`

### `system_settings`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `setting_key` | character varying(80) | no |  |
| `value` | text | yes |  |
| `value_type` | character varying(20) | no | 'STRING'::character varying |
| `category` | character varying(40) | no |  |
| `description` | character varying(300) | yes |  |
| `updated_by` | uuid | yes | → `users` |
| `updated_at` | timestamp with time zone | no | now() |

Indexes: `btree (setting_key)`

### `custom_fields`

| Column | Type | Null | Default / reference |
|---|---|---|---|
| `id` | uuid | no |  |
| `entity_type` | character varying(30) | no | 'TICKET'::character varying |
| `field_key` | character varying(60) | no |  |
| `label` | character varying(120) | no |  |
| `field_type` | character varying(20) | no |  |
| `options` | text | yes |  |
| `required` | boolean | no | false |
| `project_id` | uuid | yes | → `projects` |
| `active` | boolean | no | true |
| `created_at` | timestamp with time zone | no | now() |

Indexes: `btree (entity_type, field_key, project_id)`; `btree (id)`

