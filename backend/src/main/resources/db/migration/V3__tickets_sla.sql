-- V3 tickets and SLA

CREATE SEQUENCE ticket_number_seq START 1000;

CREATE TABLE ticket_status_config (
    code        VARCHAR(20) PRIMARY KEY,
    label       VARCHAR(60) NOT NULL,
    color       VARCHAR(20) NOT NULL,
    sort_order  INTEGER     NOT NULL,
    is_terminal BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE ticket_priority_config (
    code       VARCHAR(20) PRIMARY KEY,
    label      VARCHAR(60) NOT NULL,
    color      VARCHAR(20) NOT NULL,
    sort_order INTEGER     NOT NULL
);

CREATE TABLE tickets (
    id                    UUID PRIMARY KEY,
    ticket_number         VARCHAR(30)  NOT NULL UNIQUE,
    project_id            UUID         NOT NULL REFERENCES projects (id),
    client_id             UUID         NOT NULL REFERENCES clients (id),
    title                 VARCHAR(300) NOT NULL,
    description           TEXT,
    status                VARCHAR(20)  NOT NULL CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'REOPENED')),
    priority              VARCHAR(20)  NOT NULL CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    category_id           UUID REFERENCES ticket_categories (id) ON DELETE SET NULL,
    subcategory_id        UUID REFERENCES ticket_categories (id) ON DELETE SET NULL,
    source                VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'JIRA', 'SALESFORCE', 'EMAIL', 'API')),
    external_system       VARCHAR(40),
    external_id           VARCHAR(120),
    reporter_name         VARCHAR(150),
    reporter_email        VARCHAR(200),
    created_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    assignee_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    team_id               UUID REFERENCES teams (id) ON DELETE SET NULL,
    escalation_level      INTEGER      NOT NULL DEFAULT 0,
    reopened_count        INTEGER      NOT NULL DEFAULT 0,
    first_response_at     TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    response_due_at       TIMESTAMPTZ,
    resolution_due_at     TIMESTAMPTZ,
    sla_paused_at         TIMESTAMPTZ,
    sla_paused_seconds    BIGINT       NOT NULL DEFAULT 0,
    response_breached     BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution_breached   BOOLEAN      NOT NULL DEFAULT FALSE,
    sla_policy_id         UUID,
    custom_fields         TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_tickets_external ON tickets (external_system, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_tickets_project_status ON tickets (project_id, status);
CREATE INDEX idx_tickets_assignee ON tickets (assignee_id, status);
CREATE INDEX idx_tickets_created ON tickets (created_at DESC);
CREATE INDEX idx_tickets_client ON tickets (client_id);

CREATE TABLE ticket_comments (
    id         UUID PRIMARY KEY,
    ticket_id  UUID        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    body       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_comments_ticket ON ticket_comments (ticket_id, created_at);

CREATE TABLE ticket_attachments (
    id           UUID PRIMARY KEY,
    ticket_id    UUID         NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    uploaded_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments (ticket_id);

CREATE TABLE ticket_history (
    id         UUID PRIMARY KEY,
    ticket_id  UUID        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_name VARCHAR(150),
    action     VARCHAR(40) NOT NULL,
    field      VARCHAR(60),
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_history_ticket ON ticket_history (ticket_id, created_at);

CREATE TABLE sla_policies (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    project_id         UUID REFERENCES projects (id) ON DELETE CASCADE,
    priority           VARCHAR(20)  NOT NULL CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    response_minutes   INTEGER      NOT NULL CHECK (response_minutes > 0),
    resolution_minutes INTEGER      NOT NULL CHECK (resolution_minutes > 0),
    calendar_id        UUID REFERENCES business_calendars (id),
    business_hours_only BOOLEAN     NOT NULL DEFAULT TRUE,
    pause_on_pending   BOOLEAN      NOT NULL DEFAULT TRUE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_sla_project_priority ON sla_policies (COALESCE(project_id, '00000000-0000-0000-0000-000000000000'::uuid), priority) WHERE active;

CREATE TABLE escalation_rules (
    id                UUID PRIMARY KEY,
    project_id        UUID REFERENCES projects (id) ON DELETE CASCADE,
    priority          VARCHAR(20),
    trigger_type      VARCHAR(30) NOT NULL CHECK (trigger_type IN ('RESPONSE_BREACH', 'RESOLUTION_BREACH', 'RESOLUTION_PERCENT')),
    threshold_percent INTEGER,
    escalation_level  INTEGER     NOT NULL CHECK (escalation_level BETWEEN 1 AND 5),
    notify_role       VARCHAR(40),
    notify_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
