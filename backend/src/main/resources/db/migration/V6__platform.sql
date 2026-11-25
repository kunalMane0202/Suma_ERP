-- V6 integrations, automation, notifications, knowledge base, configuration

CREATE TABLE integrations (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(120) NOT NULL UNIQUE,
    connector_type        VARCHAR(20)  NOT NULL CHECK (connector_type IN ('JIRA', 'SALESFORCE', 'REST', 'EMAIL')),
    project_id            UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    base_url              VARCHAR(500),
    auth_type             VARCHAR(20)  NOT NULL DEFAULT 'NONE' CHECK (auth_type IN ('NONE', 'BASIC', 'BEARER', 'API_KEY', 'OAUTH2')),
    credentials_encrypted TEXT,
    query                 VARCHAR(1000),
    field_mapping         TEXT,
    sync_interval_minutes INTEGER      NOT NULL DEFAULT 15 CHECK (sync_interval_minutes >= 1),
    max_retries           INTEGER      NOT NULL DEFAULT 3,
    enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    last_sync_at          TIMESTAMPTZ,
    last_status           VARCHAR(20),
    consecutive_failures  INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE integration_sync_logs (
    id             UUID PRIMARY KEY,
    integration_id UUID        NOT NULL REFERENCES integrations (id) ON DELETE CASCADE,
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    attempt        INTEGER     NOT NULL DEFAULT 1,
    fetched        INTEGER     NOT NULL DEFAULT 0,
    created        INTEGER     NOT NULL DEFAULT 0,
    duplicates     INTEGER     NOT NULL DEFAULT 0,
    failed         INTEGER     NOT NULL DEFAULT 0,
    error          TEXT
);
CREATE INDEX idx_integration_logs ON integration_sync_logs (integration_id, started_at DESC);

CREATE TABLE automation_rules (
    id               UUID PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    trigger_event    VARCHAR(40)  NOT NULL,
    conditions       TEXT         NOT NULL DEFAULT '[]',
    actions          TEXT         NOT NULL DEFAULT '[]',
    project_id       UUID REFERENCES projects (id) ON DELETE CASCADE,
    priority         INTEGER      NOT NULL DEFAULT 100,
    stop_processing  BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_automation_trigger ON automation_rules (trigger_event, active);

CREATE TABLE automation_executions (
    id            UUID PRIMARY KEY,
    rule_id       UUID        NOT NULL REFERENCES automation_rules (id) ON DELETE CASCADE,
    trigger_event VARCHAR(40) NOT NULL,
    entity_type   VARCHAR(40),
    entity_id     VARCHAR(60),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('MATCHED', 'SKIPPED', 'FAILED')),
    actions_run   TEXT,
    error         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_automation_exec ON automation_executions (rule_id, created_at DESC);

CREATE TABLE approval_requests (
    id           UUID PRIMARY KEY,
    ticket_id    UUID        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    requested_by UUID REFERENCES users (id) ON DELETE SET NULL,
    approver_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reason       VARCHAR(500),
    decision_note VARCHAR(500),
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(1000),
    severity    VARCHAR(10)  NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    entity_type VARCHAR(40),
    entity_id   VARCHAR(60),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE email_outbox (
    id                 UUID PRIMARY KEY,
    recipient          VARCHAR(200) NOT NULL,
    subject            VARCHAR(300) NOT NULL,
    body               TEXT         NOT NULL,
    attachment_name    VARCHAR(200),
    attachment_type    VARCHAR(120),
    attachment_content BYTEA,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING')),
    attempts           INTEGER      NOT NULL DEFAULT 0,
    max_attempts       INTEGER      NOT NULL DEFAULT 5,
    next_attempt_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error         TEXT,
    related_type       VARCHAR(40),
    related_id         VARCHAR(60),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at            TIMESTAMPTZ
);
CREATE INDEX idx_email_outbox_due ON email_outbox (status, next_attempt_at);

CREATE TABLE notification_rules (
    id               UUID PRIMARY KEY,
    project_id       UUID REFERENCES projects (id) ON DELETE CASCADE,
    event_type       VARCHAR(40) NOT NULL,
    channel          VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH')),
    recipient_role   VARCHAR(40),
    escalation_level INTEGER     NOT NULL DEFAULT 0,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE kb_categories (
    id        UUID PRIMARY KEY,
    name      VARCHAR(120) NOT NULL UNIQUE,
    parent_id UUID REFERENCES kb_categories (id) ON DELETE CASCADE
);

CREATE TABLE kb_articles (
    id            UUID PRIMARY KEY,
    title         VARCHAR(250) NOT NULL,
    slug          VARCHAR(260) NOT NULL UNIQUE,
    summary       VARCHAR(500),
    content       TEXT         NOT NULL,
    article_type  VARCHAR(20)  NOT NULL CHECK (article_type IN ('SOP', 'TROUBLESHOOTING', 'FAQ', 'RESOLUTION')),
    category_id   UUID REFERENCES kb_categories (id) ON DELETE SET NULL,
    project_id    UUID REFERENCES projects (id) ON DELETE CASCADE,
    tags          VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    author_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    view_count    INTEGER      NOT NULL DEFAULT 0,
    helpful_count INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(summary, '') || ' ' || coalesce(tags, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'C')) STORED
);
CREATE INDEX idx_kb_search ON kb_articles USING GIN (search_vector);
CREATE INDEX idx_kb_project ON kb_articles (project_id);

CREATE TABLE kb_article_usage (
    id         UUID PRIMARY KEY,
    article_id UUID        NOT NULL REFERENCES kb_articles (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    ticket_id  UUID REFERENCES tickets (id) ON DELETE SET NULL,
    action     VARCHAR(20) NOT NULL CHECK (action IN ('VIEW', 'LINKED', 'HELPFUL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_usage_article ON kb_article_usage (article_id, created_at DESC);

CREATE TABLE system_settings (
    setting_key  VARCHAR(80) PRIMARY KEY,
    value        TEXT,
    value_type   VARCHAR(20) NOT NULL DEFAULT 'STRING',
    category     VARCHAR(40) NOT NULL,
    description  VARCHAR(300),
    updated_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE email_templates (
    code        VARCHAR(60) PRIMARY KEY,
    subject     VARCHAR(300) NOT NULL,
    body        TEXT         NOT NULL,
    description VARCHAR(300),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE custom_fields (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(30)  NOT NULL DEFAULT 'TICKET',
    field_key   VARCHAR(60)  NOT NULL,
    label       VARCHAR(120) NOT NULL,
    field_type  VARCHAR(20)  NOT NULL CHECK (field_type IN ('TEXT', 'NUMBER', 'DATE', 'SELECT', 'BOOLEAN')),
    options     TEXT,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    project_id  UUID REFERENCES projects (id) ON DELETE CASCADE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (entity_type, field_key, project_id)
);
