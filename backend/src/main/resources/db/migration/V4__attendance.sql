-- V4 attendance, breaks, idle monitoring and attendance reporting

-- Scope: project_id NULL = organisation-wide default. A project row overrides the global row.
CREATE TABLE attendance_settings (
    id                               UUID PRIMARY KEY,
    project_id                       UUID UNIQUE REFERENCES projects (id) ON DELETE CASCADE,
    default_work_hours               NUMERIC(4, 2),
    idle_threshold_minutes           INTEGER     NOT NULL DEFAULT 20 CHECK (idle_threshold_minutes BETWEEN 1 AND 480),
    heartbeat_timeout_minutes        INTEGER     NOT NULL DEFAULT 30 CHECK (heartbeat_timeout_minutes BETWEEN 2 AND 1440),
    missing_logout_after_hours       INTEGER     NOT NULL DEFAULT 16 CHECK (missing_logout_after_hours BETWEEN 1 AND 72),
    max_break_minutes                INTEGER CHECK (max_break_minutes IS NULL OR max_break_minutes > 0),
    max_daily_break_minutes          INTEGER CHECK (max_daily_break_minutes IS NULL OR max_daily_break_minutes > 0),
    max_breaks_per_day               INTEGER CHECK (max_breaks_per_day IS NULL OR max_breaks_per_day > 0),
    break_reminder_interval_minutes  INTEGER CHECK (break_reminder_interval_minutes IS NULL OR break_reminder_interval_minutes > 0),
    reporting_timezone               VARCHAR(60) NOT NULL DEFAULT 'UTC',
    notify_team_leader_on_idle       BOOLEAN     NOT NULL DEFAULT FALSE,
    monthly_report_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    monthly_report_hour              INTEGER     NOT NULL DEFAULT 6 CHECK (monthly_report_hour BETWEEN 0 AND 23),
    email_max_attempts               INTEGER     NOT NULL DEFAULT 5 CHECK (email_max_attempts BETWEEN 1 AND 20),
    email_retry_backoff_minutes      INTEGER     NOT NULL DEFAULT 15 CHECK (email_retry_backoff_minutes BETWEEN 1 AND 1440),
    retention_days                   INTEGER     NOT NULL DEFAULT 730 CHECK (retention_days >= 30),
    updated_by                       UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_attendance_settings_global ON attendance_settings ((project_id IS NULL)) WHERE project_id IS NULL;

CREATE TABLE break_categories (
    code       VARCHAR(30) PRIMARY KEY,
    label      VARCHAR(80) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

-- Optional per-category limits (overrides attendance_settings.max_break_minutes for that category)
CREATE TABLE break_policies (
    id                        UUID PRIMARY KEY,
    name                      VARCHAR(120) NOT NULL,
    project_id                UUID REFERENCES projects (id) ON DELETE CASCADE,
    break_category            VARCHAR(30) REFERENCES break_categories (code),
    max_duration_minutes      INTEGER CHECK (max_duration_minutes IS NULL OR max_duration_minutes > 0),
    max_per_day               INTEGER CHECK (max_per_day IS NULL OR max_per_day > 0),
    reminder_after_minutes    INTEGER CHECK (reminder_after_minutes IS NULL OR reminder_after_minutes > 0),
    active                    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attendance_sessions (
    id                UUID PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    auth_session_id   UUID        NOT NULL UNIQUE REFERENCES user_sessions (id) ON DELETE CASCADE,
    project_id        UUID REFERENCES projects (id) ON DELETE SET NULL,
    team_id           UUID REFERENCES teams (id) ON DELETE SET NULL,
    work_date         DATE        NOT NULL,
    login_at          TIMESTAMPTZ NOT NULL,
    logout_at         TIMESTAMPTZ,
    logout_source     VARCHAR(30) CHECK (logout_source IN ('USER_LOGOUT', 'SESSION_EXPIRY', 'BROWSER_UNLOAD', 'ADMIN_TERMINATED', 'RECONCILIATION', 'HEARTBEAT_TIMEOUT')),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOGGED_OUT', 'MISSING_LOGOUT', 'AUTO_CLOSED')),
    current_state     VARCHAR(20) NOT NULL DEFAULT 'WORKING' CHECK (current_state IN ('WORKING', 'ON_BREAK', 'IDLE', 'AWAY', 'OFFLINE')),
    last_heartbeat_at TIMESTAMPTZ,
    last_activity_at  TIMESTAMPTZ,
    login_ip          VARCHAR(45),
    user_agent        VARCHAR(500),
    device            VARCHAR(60),
    browser           VARCHAR(60),
    duration_seconds  BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_att_sessions_user_date ON attendance_sessions (user_id, work_date);
CREATE INDEX idx_att_sessions_date ON attendance_sessions (work_date);
CREATE INDEX idx_att_sessions_active ON attendance_sessions (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_att_sessions_project ON attendance_sessions (project_id, work_date);

CREATE TABLE attendance_events (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id  UUID REFERENCES attendance_sessions (id) ON DELETE SET NULL,
    event_type  VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source      VARCHAR(30) NOT NULL,
    details     TEXT,
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_att_events_user ON attendance_events (user_id, occurred_at DESC);
CREATE INDEX idx_att_events_type ON attendance_events (event_type, occurred_at DESC);

CREATE TABLE break_records (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id       UUID        NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    project_id       UUID REFERENCES projects (id) ON DELETE SET NULL,
    break_category   VARCHAR(30) NOT NULL REFERENCES break_categories (code),
    work_date        DATE        NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    duration_seconds BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'AUTO_CLOSED')),
    start_source     VARCHAR(30) NOT NULL,
    end_source       VARCHAR(30),
    exceeded         BOOLEAN     NOT NULL DEFAULT FALSE,
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE UNIQUE INDEX uq_break_one_active_per_user ON break_records (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_break_user_date ON break_records (user_id, work_date);
CREATE INDEX idx_break_session ON break_records (session_id);

CREATE TABLE break_reminders (
    id              UUID PRIMARY KEY,
    break_id        UUID        NOT NULL REFERENCES break_records (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reminder_type   VARCHAR(20) NOT NULL CHECK (reminder_type IN ('REMINDER', 'EXCEEDED', 'FOLLOW_UP')),
    sequence_no     INTEGER     NOT NULL DEFAULT 1,
    channel         VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    UNIQUE (break_id, reminder_type, sequence_no)
);

CREATE TABLE idle_events (
    id                    UUID PRIMARY KEY,
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id            UUID        NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    work_date             DATE        NOT NULL,
    idle_started_at       TIMESTAMPTZ NOT NULL,
    detected_at           TIMESTAMPTZ NOT NULL,
    ended_at              TIMESTAMPTZ,
    duration_seconds      BIGINT,
    status                VARCHAR(20) NOT NULL DEFAULT 'UNCONFIRMED' CHECK (status IN ('UNCONFIRMED', 'CONFIRMED', 'AUTO_RESOLVED')),
    response              VARCHAR(20) CHECK (response IN ('WORKING', 'ON_BREAK', 'AWAY')),
    responded_at          TIMESTAMPTZ,
    source                VARCHAR(30) NOT NULL DEFAULT 'BROWSER' CHECK (source IN ('BROWSER', 'SERVER_HEARTBEAT', 'DESKTOP_AGENT')),
    team_leader_notified  BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_break_id       UUID REFERENCES break_records (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_idle_one_open_per_session ON idle_events (session_id) WHERE ended_at IS NULL;
CREATE INDEX idx_idle_user_date ON idle_events (user_id, work_date);

CREATE TABLE attendance_corrections (
    id              UUID PRIMARY KEY,
    target_type     VARCHAR(20)  NOT NULL CHECK (target_type IN ('SESSION', 'BREAK', 'IDLE')),
    target_id       UUID         NOT NULL,
    field           VARCHAR(40)  NOT NULL,
    original_value  VARCHAR(100),
    corrected_value VARCHAR(100),
    reason          VARCHAR(500) NOT NULL,
    corrected_by    UUID         NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_att_corrections_target ON attendance_corrections (target_type, target_id);

CREATE TABLE attendance_daily_summary (
    id                   UUID PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    work_date            DATE        NOT NULL,
    project_id           UUID REFERENCES projects (id) ON DELETE SET NULL,
    team_id              UUID REFERENCES teams (id) ON DELETE SET NULL,
    first_login_at       TIMESTAMPTZ,
    last_logout_at       TIMESTAMPTZ,
    session_count        INTEGER     NOT NULL DEFAULT 0,
    session_seconds      BIGINT      NOT NULL DEFAULT 0,
    break_count          INTEGER     NOT NULL DEFAULT 0,
    break_seconds        BIGINT      NOT NULL DEFAULT 0,
    idle_count           INTEGER     NOT NULL DEFAULT 0,
    idle_seconds         BIGINT      NOT NULL DEFAULT 0,
    exceeded_break_count INTEGER     NOT NULL DEFAULT 0,
    missing_logout       BOOLEAN     NOT NULL DEFAULT FALSE,
    exceptions           TEXT,
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, work_date)
);
CREATE INDEX idx_att_daily_date ON attendance_daily_summary (work_date);

CREATE TABLE attendance_monthly_summary (
    id                   UUID PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id           UUID REFERENCES projects (id) ON DELETE SET NULL,
    period_year          INTEGER     NOT NULL,
    period_month         INTEGER     NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    days_present         INTEGER     NOT NULL DEFAULT 0,
    session_seconds      BIGINT      NOT NULL DEFAULT 0,
    break_count          INTEGER     NOT NULL DEFAULT 0,
    break_seconds        BIGINT      NOT NULL DEFAULT 0,
    idle_count           INTEGER     NOT NULL DEFAULT 0,
    idle_seconds         BIGINT      NOT NULL DEFAULT 0,
    exception_count      INTEGER     NOT NULL DEFAULT 0,
    missing_logout_count INTEGER     NOT NULL DEFAULT 0,
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, period_year, period_month)
);

CREATE TABLE attendance_report_logs (
    id              UUID PRIMARY KEY,
    project_id      UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    report_type     VARCHAR(20) NOT NULL CHECK (report_type IN ('MONTHLY', 'DAILY')),
    period_year     INTEGER     NOT NULL,
    period_month    INTEGER     NOT NULL,
    period_day      INTEGER     NOT NULL DEFAULT 0,
    recipient_email VARCHAR(200),
    status          VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'GENERATED', 'SENT', 'FAILED', 'RETRYING', 'SKIPPED_UNVERIFIED', 'SKIPPED_NO_RECIPIENT')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    employee_count  INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    generated_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    file_name       VARCHAR(200),
    triggered_by    VARCHAR(20) NOT NULL DEFAULT 'SCHEDULER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, report_type, period_year, period_month, period_day)
);
CREATE INDEX idx_att_report_status ON attendance_report_logs (status, next_retry_at);
