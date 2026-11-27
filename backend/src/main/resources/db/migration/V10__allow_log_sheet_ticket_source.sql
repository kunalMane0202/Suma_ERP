ALTER TABLE tickets DROP CONSTRAINT tickets_source_check;

ALTER TABLE tickets
    ADD CONSTRAINT tickets_source_check
    CHECK (source IN ('MANUAL', 'JIRA', 'SALESFORCE', 'EMAIL', 'API', 'LOG_SHEET'));
