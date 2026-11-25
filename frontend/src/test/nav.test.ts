import { visibleNav } from '@/routes/nav';
import type { Me } from '@/types/api';

const base: Me = { id: '1', employeeCode: 'E', username: 'u', fullName: 'U', email: 'u@x', role: 'L1', permissions: [], themePreference: 'LIGHT', sessionId: 's', projects: [], projectLead: false, teamLeader: false };
const labels = (u: Me) => visibleNav(u).flatMap((g) => g.items.map((i) => i.label));

describe('role-based navigation', () => {
  it('L1 engineers see their work areas only', () => {
    const l = labels({ ...base, permissions: ['DASHBOARD_VIEW', 'TICKET_READ', 'KB_READ', 'ATTENDANCE_SELF'] });
    expect(l).toEqual(expect.arrayContaining(['Dashboard', 'Tickets', 'Knowledge base', 'My attendance']));
    expect(l).not.toContain('Login security');
    expect(l).not.toContain('Team attendance');
    expect(l).not.toContain('Configuration');
  });
  it('Project Leads get team attendance and reports through their designation', () => {
    const l = labels({ ...base, role: 'L3', projectLead: true, permissions: ['ATTENDANCE_SELF'] });
    expect(l).toContain('Team attendance');
    expect(l).toContain('Reports');
  });
  it('administrators see administration', () => {
    const l = labels({ ...base, role: 'ADMIN', permissions: ['SECURITY_VIEW', 'AUDIT_VIEW', 'SETTINGS_MANAGE', 'AUTOMATION_MANAGE', 'INTEGRATION_MANAGE'] });
    expect(l).toEqual(expect.arrayContaining(['Login security', 'Audit trail', 'Configuration', 'Automation', 'Integrations']));
  });
});
