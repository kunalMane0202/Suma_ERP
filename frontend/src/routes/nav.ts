import type { LucideIcon } from 'lucide-react';
import { BarChart3, BookOpen, Building2, CalendarClock, FolderKanban, Gauge, Plug, Settings, ShieldCheck, ScrollText, Ticket, Timer, Users, Workflow, Bell, UsersRound } from 'lucide-react';
import type { Me } from '@/types/api';

export interface NavItem { to: string; label: string; icon: LucideIcon; visible: (u: Me) => boolean }
export interface NavGroup { label: string; items: NavItem[] }

const has = (u: Me, ...p: string[]) => p.some((x) => u.permissions.includes(x));

export const NAV: NavGroup[] = [
  {
    label: 'Operations',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: Gauge, visible: (u) => has(u, 'DASHBOARD_VIEW') },
      { to: '/tickets', label: 'Tickets', icon: Ticket, visible: (u) => has(u, 'TICKET_READ') },
      { to: '/knowledge', label: 'Knowledge base', icon: BookOpen, visible: (u) => has(u, 'KB_READ') },
      { to: '/notifications', label: 'Notifications', icon: Bell, visible: () => true },
    ],
  },
  {
    label: 'Workforce',
    items: [
      { to: '/attendance', label: 'My attendance', icon: Timer, visible: (u) => has(u, 'ATTENDANCE_SELF') },
      { to: '/attendance/team', label: 'Team attendance', icon: CalendarClock, visible: (u) => has(u, 'ATTENDANCE_TEAM_VIEW', 'ATTENDANCE_ORG_VIEW') || u.projectLead },
      { to: '/people', label: 'People & teams', icon: Users, visible: (u) => has(u, 'USER_READ', 'TEAM_READ') },
    ],
  },
  {
    label: 'Accounts',
    items: [
      { to: '/clients', label: 'Clients', icon: Building2, visible: (u) => has(u, 'CLIENT_READ') },
      { to: '/projects', label: 'Projects', icon: FolderKanban, visible: (u) => has(u, 'PROJECT_READ') },
      { to: '/sla', label: 'SLA', icon: UsersRound, visible: (u) => has(u, 'SLA_READ') },
      { to: '/reports', label: 'Reports', icon: BarChart3, visible: (u) => has(u, 'REPORT_VIEW', 'ATTENDANCE_REPORT_VIEW') || u.projectLead },
    ],
  },
  {
    label: 'Administration',
    items: [
      { to: '/automation', label: 'Automation', icon: Workflow, visible: (u) => has(u, 'AUTOMATION_MANAGE') },
      { to: '/integrations', label: 'Integrations', icon: Plug, visible: (u) => has(u, 'INTEGRATION_MANAGE') },
      { to: '/security', label: 'Login security', icon: ShieldCheck, visible: (u) => has(u, 'SECURITY_VIEW') },
      { to: '/audit', label: 'Audit trail', icon: ScrollText, visible: (u) => has(u, 'AUDIT_VIEW') },
      { to: '/settings', label: 'Configuration', icon: Settings, visible: (u) => has(u, 'SETTINGS_MANAGE', 'ATTENDANCE_MANAGE', 'ROLE_MANAGE') },
    ],
  },
];

export function visibleNav(u: Me): NavGroup[] {
  return NAV.map((g) => ({ ...g, items: g.items.filter((i) => i.visible(u)) })).filter((g) => g.items.length > 0);
}
