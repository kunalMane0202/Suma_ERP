CREATE TABLE project_budget_uploads (
    id           UUID PRIMARY KEY,
    project_id   UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    uploaded_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    file_name    VARCHAR(200) NOT NULL,
    currency     VARCHAR(3) NOT NULL CHECK (currency IN ('INR', 'USD', 'EUR', 'GBP', 'AUD', 'CAD', 'JPY', 'SGD')),
    total_budget NUMERIC(18, 2) NOT NULL CHECK (total_budget >= 0),
    item_count   INTEGER NOT NULL CHECK (item_count > 0),
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_budget_uploads_latest
    ON project_budget_uploads (project_id, uploaded_at DESC);

CREATE TABLE project_budget_items (
    id          UUID PRIMARY KEY,
    upload_id   UUID NOT NULL REFERENCES project_budget_uploads (id) ON DELETE CASCADE,
    category    VARCHAR(120) NOT NULL,
    item        VARCHAR(250) NOT NULL,
    quantity    NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    unit_cost   NUMERIC(18, 2) NOT NULL CHECK (unit_cost >= 0),
    amount      NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    sort_order  INTEGER NOT NULL
);
CREATE INDEX idx_project_budget_items_upload ON project_budget_items (upload_id, sort_order);
