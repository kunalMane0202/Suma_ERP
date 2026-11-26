CREATE TABLE account_registration_requests (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ,
    reviewed_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_user_id UUID REFERENCES users (id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX uq_account_registration_pending_email
    ON account_registration_requests (lower(email)) WHERE status = 'PENDING';
CREATE INDEX idx_account_registration_status_created
    ON account_registration_requests (status, created_at);

CREATE TABLE password_action_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose    VARCHAR(20) NOT NULL CHECK (purpose IN ('SETUP', 'RESET')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX idx_password_action_tokens_user ON password_action_tokens (user_id, purpose);
