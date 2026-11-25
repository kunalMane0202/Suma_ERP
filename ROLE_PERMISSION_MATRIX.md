# Role and permission matrix

Six predefined roles. Permissions are stored in `role_permissions` and can be changed by an administrator under **Configuration → Roles & permissions** (audited; ADMIN cannot remove its own ROLE_MANAGE / SECURITY_MANAGE / USER_WRITE). Every permission is enforced on the server with `@PreAuthorize`; the UI only hides what the server would refuse.

## Default matrix

| Module | Permission | Meaning | ADMIN | MANAGER | TEAM_LEADER | L1 | L2 | L3 |
|---|---|---|---|---|---|---|---|---|
| Attendance | `ATTENDANCE_MANAGE` | Manage attendance policies and corrections | ✔ |  |  |  |  |  |
| Attendance | `ATTENDANCE_ORG_VIEW` | View organisation-wide attendance | ✔ |  |  |  |  |  |
| Attendance | `ATTENDANCE_REPORT_VIEW` | View and export attendance reports | ✔ | ✔ | ✔ |  |  |  |
| Attendance | `ATTENDANCE_SELF` | Record own attendance and breaks | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Attendance | `ATTENDANCE_TEAM_VIEW` | View attendance of own teams/projects | ✔ | ✔ | ✔ |  |  |  |
| Automation | `AUTOMATION_MANAGE` | Manage automation rules | ✔ |  |  |  |  |  |
| Client | `CLIENT_READ` | View clients | ✔ | ✔ |  |  |  |  |
| Client | `CLIENT_WRITE` | Create and edit clients | ✔ |  |  |  |  |  |
| Dashboard | `DASHBOARD_VIEW` | View operational dashboard | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Integration | `INTEGRATION_MANAGE` | Manage integrations | ✔ |  |  |  |  |  |
| Knowledge | `KB_READ` | Read knowledge base | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Knowledge | `KB_WRITE` | Author knowledge base articles | ✔ | ✔ | ✔ |  | ✔ | ✔ |
| Notification | `NOTIFICATION_MANAGE` | Manage notification rules | ✔ |  |  |  |  |  |
| Project | `PROJECT_READ` | View projects | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Project | `PROJECT_WRITE` | Create and edit projects | ✔ |  |  |  |  |  |
| Report | `REPORT_EXPORT` | Export reports | ✔ | ✔ | ✔ |  |  |  |
| Report | `REPORT_VIEW` | View operational reports | ✔ | ✔ | ✔ |  |  |  |
| Security | `AUDIT_VIEW` | View audit trails | ✔ | ✔ |  |  |  |  |
| Security | `ROLE_MANAGE` | Manage role permissions | ✔ |  |  |  |  |  |
| Security | `SECURITY_MANAGE` | Terminate sessions, manage IP rules | ✔ |  |  |  |  |  |
| Security | `SECURITY_VIEW` | View login security and IP activity | ✔ |  |  |  |  |  |
| Settings | `SETTINGS_MANAGE` | Manage system configuration | ✔ |  |  |  |  |  |
| Sla | `SLA_READ` | View SLA policies | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Sla | `SLA_WRITE` | Manage SLA policies, calendars, holidays | ✔ |  |  |  |  |  |
| Team | `TEAM_READ` | View teams | ✔ | ✔ | ✔ |  |  |  |
| Team | `TEAM_WRITE` | Manage teams | ✔ |  |  |  |  |  |
| Ticket | `TICKET_ASSIGN` | Assign and reassign tickets | ✔ | ✔ | ✔ |  |  |  |
| Ticket | `TICKET_CREATE` | Create tickets | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Ticket | `TICKET_ESCALATE` | Escalate tickets | ✔ | ✔ | ✔ |  | ✔ | ✔ |
| Ticket | `TICKET_READ` | View tickets | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Ticket | `TICKET_UPDATE` | Update ticket status, comments, attachments | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| User | `USER_READ` | View employees | ✔ | ✔ | ✔ |  |  |  |
| User | `USER_WRITE` | Create and edit employees | ✔ |  |  |  |  |  |

## Project Lead

Project Lead is **not a seventh role** (the business hierarchy already has six). It is a per-project designation:

- `projects.project_lead_id` and/or `project_members.designation = PROJECT_LEAD`;
- `projects.project_lead_email` with `project_lead_email_verified` — the monthly attendance report is emailed only to a verified address. The address is auto-verified only when it equals the lead's own account email; otherwise an administrator must verify it explicitly (audited).

A Project Lead, whatever their role, can additionally:

- view attendance (live status, daily and monthly reports, exports) for members of the projects they lead;
- see the monthly report delivery history for those projects and download the reports;
- open Team attendance and Reports in the navigation.

## Data isolation (applies on top of permissions)

| Caller | Projects / clients visible | Tickets visible | Attendance visible |
|---|---|---|---|
| ADMIN | all | all | all |
| MANAGER | projects they manage, are members of, or whose client they account-manage | tickets of those projects | members of those projects (with ATTENDANCE_TEAM_VIEW) |
| TEAM_LEADER | projects they lead or belong to; teams they lead | tickets of those projects | members of teams they lead and projects they lead |
| L1 / L2 / L3 | projects they are allocated to | tickets assigned to them, unassigned tickets of their projects, tickets they created | their own records only (plus led projects if Project Lead) |

Records outside the caller's scope return **404** (not 403) so their existence is not disclosed. Unauthorized endpoint calls return 403 and are logged as `ACCESS_DENIED` in the security monitoring tables.

## Security administration actions (all audited)

Terminate sessions, create/enable/disable/delete IP allow/deny rules, change role permissions, reset passwords, unlock accounts, verify Project Lead emails, attendance corrections, settings changes, integration credential updates.
