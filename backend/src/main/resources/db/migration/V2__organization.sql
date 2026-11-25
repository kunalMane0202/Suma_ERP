-- V2 clients, projects, teams, categories

CREATE TABLE clients (
    id                 UUID PRIMARY KEY,
    code               VARCHAR(30)  NOT NULL UNIQUE,
    name               VARCHAR(200) NOT NULL,
    industry           VARCHAR(100),
    email              VARCHAR(200),
    phone              VARCHAR(40),
    address            VARCHAR(500),
    website            VARCHAR(200),
    account_manager_id UUID REFERENCES users (id) ON DELETE SET NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    configuration      TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_clients_status ON clients (status);

CREATE TABLE client_contacts (
    id         UUID PRIMARY KEY,
    client_id  UUID         NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(200),
    phone      VARCHAR(40),
    role_title VARCHAR(100),
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_client_contacts_client ON client_contacts (client_id);

CREATE TABLE business_calendars (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    timezone   VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE business_hours (
    id          UUID PRIMARY KEY,
    calendar_id UUID     NOT NULL REFERENCES business_calendars (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL,
    CHECK (end_time > start_time),
    UNIQUE (calendar_id, day_of_week)
);

CREATE TABLE holidays (
    id           UUID PRIMARY KEY,
    calendar_id  UUID         NOT NULL REFERENCES business_calendars (id) ON DELETE CASCADE,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(150) NOT NULL,
    UNIQUE (calendar_id, holiday_date)
);

CREATE TABLE projects (
    id                          UUID PRIMARY KEY,
    client_id                   UUID         NOT NULL REFERENCES clients (id),
    code                        VARCHAR(30)  NOT NULL UNIQUE,
    name                        VARCHAR(200) NOT NULL,
    description                 TEXT,
    manager_id                  UUID REFERENCES users (id) ON DELETE SET NULL,
    team_leader_id              UUID REFERENCES users (id) ON DELETE SET NULL,
    project_lead_id             UUID REFERENCES users (id) ON DELETE SET NULL,
    project_lead_email          VARCHAR(200),
    project_lead_email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    calendar_id                 UUID REFERENCES business_calendars (id),
    support_hours               VARCHAR(100),
    timezone                    VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    status                      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    start_date                  DATE,
    end_date                    DATE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_projects_client ON projects (client_id);
CREATE INDEX idx_projects_status ON projects (status);

CREATE TABLE project_members (
    project_id         UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    designation        VARCHAR(30) NOT NULL DEFAULT 'MEMBER' CHECK (designation IN ('MEMBER', 'PROJECT_LEAD', 'TEAM_LEADER', 'MANAGER')),
    allocation_percent INTEGER     NOT NULL DEFAULT 100 CHECK (allocation_percent BETWEEN 0 AND 100),
    is_primary         BOOLEAN     NOT NULL DEFAULT TRUE,
    assigned_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX idx_project_members_user ON project_members (user_id);

CREATE TABLE project_shifts (
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    shift_id   UUID NOT NULL REFERENCES shifts (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, shift_id)
);

CREATE TABLE teams (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120) NOT NULL UNIQUE,
    description    VARCHAR(500),
    project_id     UUID REFERENCES projects (id) ON DELETE SET NULL,
    team_leader_id UUID REFERENCES users (id) ON DELETE SET NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_teams_project ON teams (project_id);

CREATE TABLE team_members (
    team_id   UUID        NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, user_id)
);
CREATE INDEX idx_team_members_user ON team_members (user_id);

CREATE TABLE ticket_categories (
    id                 UUID PRIMARY KEY,
    project_id         UUID REFERENCES projects (id) ON DELETE CASCADE,
    parent_id          UUID REFERENCES ticket_categories (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    keywords           VARCHAR(500),
    default_team_id    UUID REFERENCES teams (id) ON DELETE SET NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_categories_project ON ticket_categories (project_id);
