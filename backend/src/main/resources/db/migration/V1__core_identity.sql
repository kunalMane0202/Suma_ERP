-- SUMA Tracking Tool - V1 core identity, sessions, audit, scheduling infrastructure
-- All timestamps are timestamptz and stored in UTC.

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(300)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE shifts (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    start_time TIME         NOT NULL,
    end_time   TIME         NOT NULL,
    timezone   VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    employee_code      VARCHAR(40)  NOT NULL UNIQUE,
    username           VARCHAR(80)  NOT NULL UNIQUE,
    email              VARCHAR(200) NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    full_name          VARCHAR(150) NOT NULL,
    phone              VARCHAR(40),
    job_title          VARCHAR(100),
    role_id            UUID         NOT NULL REFERENCES roles (id),
    shift_id           UUID REFERENCES shifts (id),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    theme_preference   VARCHAR(10)  NOT NULL DEFAULT 'LIGHT' CHECK (theme_preference IN ('LIGHT', 'DARK')),
    timezone           VARCHAR(60),
    max_open_tickets   INTEGER,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    last_login_ip      VARCHAR(45),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_users_role ON users (role_id);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_weekly_offs (
    user_id     UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    PRIMARY KEY (user_id, day_of_week)
);

-- One row per authenticated login. The access token carries this id as the "sid" claim.
CREATE TABLE user_sessions (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOGGED_OUT', 'EXPIRED', 'TERMINATED')),
    auth_method    VARCHAR(30) NOT NULL DEFAULT 'PASSWORD',
    ip_address     VARCHAR(45),
    ip_version     SMALLINT,
    last_ip        VARCHAR(45),
    user_agent     VARCHAR(500),
    device         VARCHAR(60),
    browser        VARCHAR(60),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    ended_at       TIMESTAMPTZ,
    ended_reason   VARCHAR(200),
    ended_by       UUID REFERENCES users (id)
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, created_at DESC);
CREATE INDEX idx_user_sessions_active ON user_sessions (status) WHERE status = 'ACTIVE';

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY,
    actor_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_username VARCHAR(80),
    action         VARCHAR(80)  NOT NULL,
    entity_type    VARCHAR(60),
    entity_id      VARCHAR(60),
    details        TEXT,
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_logs (actor_id);

-- ShedLock distributed lock table (prevents duplicate scheduled executions)
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE job_execution_log (
    id              UUID PRIMARY KEY,
    job_name        VARCHAR(80) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    items_processed INTEGER     NOT NULL DEFAULT 0,
    message         TEXT
);
CREATE INDEX idx_job_exec_name ON job_execution_log (job_name, started_at DESC);
