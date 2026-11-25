-- V7 reference data: roles, permissions, default mappings, configuration defaults.
-- Business thresholds that the company has not defined (break limits, SLA targets) are intentionally left NULL / unseeded.

INSERT INTO roles (id, code, name, description) VALUES
 (gen_random_uuid(), 'ADMIN', 'Administrator', 'Full platform administration'),
 (gen_random_uuid(), 'MANAGER', 'Manager', 'Monitors authorised clients, projects and teams'),
 (gen_random_uuid(), 'TEAM_LEADER', 'Team Leader', 'Monitors assigned team tickets and attendance'),
 (gen_random_uuid(), 'L1', 'L1 Support Engineer', 'First-line support'),
 (gen_random_uuid(), 'L2', 'L2 Support Engineer', 'Second-line support'),
 (gen_random_uuid(), 'L3', 'L3 Support Engineer', 'Third-line / expert support');

INSERT INTO permissions (id, code, module, description) VALUES
 (gen_random_uuid(), 'DASHBOARD_VIEW', 'DASHBOARD', 'View operational dashboard'),
 (gen_random_uuid(), 'CLIENT_READ', 'CLIENT', 'View clients'),
 (gen_random_uuid(), 'CLIENT_WRITE', 'CLIENT', 'Create and edit clients'),
 (gen_random_uuid(), 'PROJECT_READ', 'PROJECT', 'View projects'),
 (gen_random_uuid(), 'PROJECT_WRITE', 'PROJECT', 'Create and edit projects'),
 (gen_random_uuid(), 'USER_READ', 'USER', 'View employees'),
 (gen_random_uuid(), 'USER_WRITE', 'USER', 'Create and edit employees'),
 (gen_random_uuid(), 'TEAM_READ', 'TEAM', 'View teams'),
 (gen_random_uuid(), 'TEAM_WRITE', 'TEAM', 'Manage teams'),
 (gen_random_uuid(), 'TICKET_READ', 'TICKET', 'View tickets'),
 (gen_random_uuid(), 'TICKET_CREATE', 'TICKET', 'Create tickets'),
 (gen_random_uuid(), 'TICKET_UPDATE', 'TICKET', 'Update ticket status, comments, attachments'),
 (gen_random_uuid(), 'TICKET_ASSIGN', 'TICKET', 'Assign and reassign tickets'),
 (gen_random_uuid(), 'TICKET_ESCALATE', 'TICKET', 'Escalate tickets'),
 (gen_random_uuid(), 'SLA_READ', 'SLA', 'View SLA policies'),
 (gen_random_uuid(), 'SLA_WRITE', 'SLA', 'Manage SLA policies, calendars, holidays'),
 (gen_random_uuid(), 'INTEGRATION_MANAGE', 'INTEGRATION', 'Manage integrations'),
 (gen_random_uuid(), 'AUTOMATION_MANAGE', 'AUTOMATION', 'Manage automation rules'),
 (gen_random_uuid(), 'REPORT_VIEW', 'REPORT', 'View operational reports'),
 (gen_random_uuid(), 'REPORT_EXPORT', 'REPORT', 'Export reports'),
 (gen_random_uuid(), 'NOTIFICATION_MANAGE', 'NOTIFICATION', 'Manage notification rules'),
 (gen_random_uuid(), 'KB_READ', 'KNOWLEDGE', 'Read knowledge base'),
 (gen_random_uuid(), 'KB_WRITE', 'KNOWLEDGE', 'Author knowledge base articles'),
 (gen_random_uuid(), 'AUDIT_VIEW', 'SECURITY', 'View audit trails'),
 (gen_random_uuid(), 'SECURITY_VIEW', 'SECURITY', 'View login security and IP activity'),
 (gen_random_uuid(), 'SECURITY_MANAGE', 'SECURITY', 'Terminate sessions, manage IP rules'),
 (gen_random_uuid(), 'ROLE_MANAGE', 'SECURITY', 'Manage role permissions'),
 (gen_random_uuid(), 'SETTINGS_MANAGE', 'SETTINGS', 'Manage system configuration'),
 (gen_random_uuid(), 'ATTENDANCE_SELF', 'ATTENDANCE', 'Record own attendance and breaks'),
 (gen_random_uuid(), 'ATTENDANCE_TEAM_VIEW', 'ATTENDANCE', 'View attendance of own teams/projects'),
 (gen_random_uuid(), 'ATTENDANCE_ORG_VIEW', 'ATTENDANCE', 'View organisation-wide attendance'),
 (gen_random_uuid(), 'ATTENDANCE_MANAGE', 'ATTENDANCE', 'Manage attendance policies and corrections'),
 (gen_random_uuid(), 'ATTENDANCE_REPORT_VIEW', 'ATTENDANCE', 'View and export attendance reports');

-- ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'DASHBOARD_VIEW','CLIENT_READ','PROJECT_READ','USER_READ','TEAM_READ','TICKET_READ','TICKET_CREATE','TICKET_UPDATE',
  'TICKET_ASSIGN','TICKET_ESCALATE','SLA_READ','REPORT_VIEW','REPORT_EXPORT','KB_READ','KB_WRITE','AUDIT_VIEW',
  'ATTENDANCE_SELF','ATTENDANCE_TEAM_VIEW','ATTENDANCE_REPORT_VIEW')
WHERE r.code = 'MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'DASHBOARD_VIEW','PROJECT_READ','USER_READ','TEAM_READ','TICKET_READ','TICKET_CREATE','TICKET_UPDATE','TICKET_ASSIGN',
  'TICKET_ESCALATE','SLA_READ','REPORT_VIEW','REPORT_EXPORT','KB_READ','KB_WRITE','ATTENDANCE_SELF',
  'ATTENDANCE_TEAM_VIEW','ATTENDANCE_REPORT_VIEW')
WHERE r.code = 'TEAM_LEADER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'DASHBOARD_VIEW','PROJECT_READ','TICKET_READ','TICKET_CREATE','TICKET_UPDATE','SLA_READ','KB_READ','ATTENDANCE_SELF')
WHERE r.code = 'L1';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'DASHBOARD_VIEW','PROJECT_READ','TICKET_READ','TICKET_CREATE','TICKET_UPDATE','TICKET_ESCALATE','SLA_READ','KB_READ','KB_WRITE','ATTENDANCE_SELF')
WHERE r.code IN ('L2', 'L3');

INSERT INTO ticket_status_config (code, label, color, sort_order, is_terminal) VALUES
 ('NEW', 'New', '#3b82f6', 1, FALSE),
 ('ASSIGNED', 'Assigned', '#6366f1', 2, FALSE),
 ('IN_PROGRESS', 'In Progress', '#0ea5e9', 3, FALSE),
 ('PENDING', 'Pending', '#f59e0b', 4, FALSE),
 ('RESOLVED', 'Resolved', '#10b981', 5, FALSE),
 ('CLOSED', 'Closed', '#64748b', 6, TRUE),
 ('REOPENED', 'Reopened', '#ef4444', 7, FALSE);

INSERT INTO ticket_priority_config (code, label, color, sort_order) VALUES
 ('CRITICAL', 'Critical', '#dc2626', 1),
 ('HIGH', 'High', '#f97316', 2),
 ('MEDIUM', 'Medium', '#eab308', 3),
 ('LOW', 'Low', '#22c55e', 4);

-- Default calendar: 24x7 so that SLA works before an administrator defines business hours.
INSERT INTO business_calendars (id, name, timezone, is_default) VALUES (gen_random_uuid(), 'Default (24x7)', 'UTC', TRUE);

-- Global attendance settings. Break thresholds are NULL until an administrator configures them.
INSERT INTO attendance_settings (id, project_id, idle_threshold_minutes) VALUES (gen_random_uuid(), NULL, 20);

INSERT INTO break_categories (code, label, sort_order) VALUES
 ('GENERAL', 'General break', 1),
 ('MEAL', 'Meal break', 2),
 ('PERSONAL', 'Personal', 3),
 ('MEETING', 'Offline meeting', 4);

INSERT INTO system_settings (setting_key, value, value_type, category, description) VALUES
 ('app.name', 'SUMA Tracking Tool', 'STRING', 'GENERAL', 'Application display name'),
 ('app.display_timezone', 'UTC', 'STRING', 'GENERAL', 'Default display timezone'),
 ('ticket.auto_close_resolved_days', '7', 'NUMBER', 'TICKET', 'Days after which RESOLVED tickets are auto-closed (0 = never)'),
 ('ticket.pending_reminder_hours', '24', 'NUMBER', 'TICKET', 'Remind assignee when ticket is PENDING longer than this'),
 ('attachments.max_size_mb', '10', 'NUMBER', 'TICKET', 'Maximum attachment size'),
 ('attachments.allowed_types', 'pdf,png,jpg,jpeg,gif,txt,log,csv,xlsx,docx,zip', 'STRING', 'TICKET', 'Allowed attachment extensions'),
 ('security.max_failed_logins', '5', 'NUMBER', 'SECURITY', 'Failed attempts before temporary account lock'),
 ('security.lock_minutes', '15', 'NUMBER', 'SECURITY', 'Temporary lock duration'),
 ('security.failed_attempt_window_minutes', '15', 'NUMBER', 'SECURITY', 'Window for repeated failed attempt detection'),
 ('security.failed_attempt_alert_threshold', '5', 'NUMBER', 'SECURITY', 'Failed attempts per IP within the window flagged as suspicious'),
 ('retention.security_log_days', '365', 'NUMBER', 'RETENTION', 'Retention for login / anonymous / IP logs'),
 ('retention.audit_log_days', '1095', 'NUMBER', 'RETENTION', 'Retention for audit logs'),
 ('retention.notification_days', '180', 'NUMBER', 'RETENTION', 'Retention for notifications'),
 ('dashboard.widgets', 'kpis,trend,priority,status,team,workforce', 'STRING', 'DASHBOARD', 'Enabled dashboard widgets');

INSERT INTO email_templates (code, subject, body, description) VALUES
 ('TICKET_ASSIGNED', 'Ticket {{ticketNumber}} assigned to you', 'Hello {{name}},\n\nTicket {{ticketNumber}} - {{title}} (priority {{priority}}) has been assigned to you.\n\nSUMA Tracking Tool', 'Ticket assignment'),
 ('SLA_BREACH', 'SLA breach: {{ticketNumber}}', 'Ticket {{ticketNumber}} - {{title}} has breached its {{slaType}} SLA.', 'SLA breach alert'),
 ('MONTHLY_ATTENDANCE', 'Monthly attendance report - {{project}} - {{period}}', 'Hello {{name}},\n\nPlease find attached the consolidated attendance report for project {{project}} for {{period}} ({{employeeCount}} employees).\n\nThis report was generated automatically by SUMA Tracking Tool.', 'Monthly attendance report to Project Lead'),
 ('MISSING_LOGOUT', 'Missing logout recorded for {{date}}', 'Hello {{name}},\n\nYour session from {{date}} did not have a logout and was closed by reconciliation. Please contact your team leader if this is incorrect.', 'Missing logout notification');
