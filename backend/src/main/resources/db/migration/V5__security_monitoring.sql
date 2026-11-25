-- V5 login security and IP activity monitoring. Passwords and tokens are NEVER stored here.

CREATE TABLE login_activity_logs (
    id                 UUID PRIMARY KEY,
    user_id            UUID REFERENCES users (id) ON DELETE SET NULL,
    username_attempted VARCHAR(120),
    success            BOOLEAN     NOT NULL,
    failure_category   VARCHAR(30) CHECK (failure_category IN ('UNKNOWN_USER', 'INVALID_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_DISABLED', 'BLOCKED_IP', 'RATE_LIMITED')),
    ip_address         VARCHAR(45) NOT NULL,
    ip_version         SMALLINT    NOT NULL,
    session_id         UUID REFERENCES user_sessions (id) ON DELETE SET NULL,
    user_agent         VARCHAR(500),
    device             VARCHAR(60),
    browser            VARCHAR(60),
    auth_method        VARCHAR(30) NOT NULL DEFAULT 'PASSWORD',
    attempt_count      INTEGER     NOT NULL DEFAULT 1,
    suspicious         BOOLEAN     NOT NULL DEFAULT FALSE,
    suspicious_reasons VARCHAR(500),
    geo_location       VARCHAR(120),
    correlation_id     VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_activity_created ON login_activity_logs (created_at DESC);
CREATE INDEX idx_login_activity_ip ON login_activity_logs (ip_address, created_at DESC);
CREATE INDEX idx_login_activity_user ON login_activity_logs (user_id, created_at DESC);
CREATE INDEX idx_login_activity_failed ON login_activity_logs (success, created_at DESC);

CREATE TABLE anonymous_access_logs (
    id             UUID PRIMARY KEY,
    ip_address     VARCHAR(45)  NOT NULL,
    ip_version     SMALLINT     NOT NULL,
    http_method    VARCHAR(10)  NOT NULL,
    endpoint       VARCHAR(500) NOT NULL,
    category       VARCHAR(30)  NOT NULL CHECK (category IN ('MISSING_AUTH', 'INVALID_TOKEN', 'EXPIRED_SESSION', 'ACCESS_DENIED', 'BLOCKED_IP', 'REVOKED_SESSION')),
    status_code    INTEGER      NOT NULL,
    user_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    username       VARCHAR(120),
    user_agent     VARCHAR(500),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_anon_created ON anonymous_access_logs (created_at DESC);
CREATE INDEX idx_anon_ip ON anonymous_access_logs (ip_address, created_at DESC);

-- Unified IP timeline (one row per security-relevant event from an IP)
CREATE TABLE ip_activity_logs (
    id            UUID PRIMARY KEY,
    ip_address    VARCHAR(45) NOT NULL,
    ip_version    SMALLINT    NOT NULL,
    user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    activity_type VARCHAR(30) NOT NULL CHECK (activity_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'UNKNOWN_USER', 'ANONYMOUS_ACCESS', 'ACCESS_DENIED', 'IP_CHANGE', 'BLOCKED', 'SUSPICIOUS')),
    details       VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ip_activity_ip ON ip_activity_logs (ip_address, created_at DESC);
CREATE INDEX idx_ip_activity_type ON ip_activity_logs (activity_type, created_at DESC);
CREATE INDEX idx_ip_activity_user ON ip_activity_logs (user_id, created_at DESC);

CREATE TABLE trusted_ip_rules (
    id          UUID PRIMARY KEY,
    cidr        VARCHAR(60)  NOT NULL,
    rule_type   VARCHAR(10)  NOT NULL CHECK (rule_type IN ('ALLOW', 'DENY')),
    description VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMPTZ,
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (cidr, rule_type)
);

CREATE TABLE session_activity_logs (
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action     VARCHAR(30) NOT NULL CHECK (action IN ('CREATED', 'REFRESHED', 'LOGGED_OUT', 'EXPIRED', 'TERMINATED', 'IP_CHANGED', 'TOKEN_REUSE_DETECTED')),
    ip_address VARCHAR(45),
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    reason     VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_activity_session ON session_activity_logs (session_id, created_at);
