import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AuthProvider, useAuth } from '@/contexts/AuthContext';
import { ThemeProvider, useTheme } from '@/contexts/ThemeContext';
import { TooltipProvider } from '@/components/ui/tooltip';
import { AppShell } from '@/layouts/AppShell';
const LoginPage = lazy(() => import('@/modules/auth/LoginPage').then((m) => ({ default: m.LoginPage })));
import { Skeleton } from '@/components/ui/skeleton';
import type { Me } from '@/types/api';

const DashboardPage = lazy(() => import('@/modules/dashboard/DashboardPage'));
const TicketsPage = lazy(() => import('@/modules/tickets/TicketsPage'));
const TicketDetailPage = lazy(() => import('@/modules/tickets/TicketDetailPage'));
const AttendancePage = lazy(() => import('@/modules/attendance/AttendancePage'));
const TeamAttendancePage = lazy(() => import('@/modules/attendance/TeamAttendancePage'));
const ClientsPage = lazy(() => import('@/modules/clients/ClientsPage'));
const ProjectsPage = lazy(() => import('@/modules/projects/ProjectsPage'));
const ProjectDetailPage = lazy(() => import('@/modules/projects/ProjectDetailPage'));
const PeoplePage = lazy(() => import('@/modules/users/PeoplePage'));
const SlaPage = lazy(() => import('@/modules/sla/SlaPage'));
const ReportsPage = lazy(() => import('@/modules/reports/ReportsPage'));
const KnowledgePage = lazy(() => import('@/modules/knowledge/KnowledgePage'));
const AutomationPage = lazy(() => import('@/modules/automation/AutomationPage'));
const IntegrationsPage = lazy(() => import('@/modules/integrations/IntegrationsPage'));
const SecurityPage = lazy(() => import('@/modules/security/SecurityPage'));
const AuditPage = lazy(() => import('@/modules/audit/AuditPage'));
const SettingsPage = lazy(() => import('@/modules/settings/SettingsPage'));
const NotificationsPage = lazy(() => import('@/modules/notifications/NotificationsPage'));
const ProfilePage = lazy(() => import('@/modules/auth/ProfilePage'));

function Loading() {
  return (
    <div className="space-y-4" role="status" aria-label="Loading page">
      <Skeleton className="h-8 w-64" />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div>
      <Skeleton className="h-72" />
    </div>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  const location = useLocation();
  if (!ready) return <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">Loading SUMA…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

function Guard({ allow, children }: { allow: (u: Me) => boolean; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !allow(user)) return <Forbidden />;
  return <>{children}</>;
}

function Forbidden() {
  return (
    <div className="mx-auto max-w-md py-24 text-center">
      <h1 className="text-xl font-semibold">You don't have access to this page</h1>
      <p className="mt-2 text-sm text-muted-foreground">Your role does not include the permission this page needs. Ask an administrator if you think this is wrong.</p>
    </div>
  );
}

export function homeFor(u: Me): string {
  if (u.permissions.includes('DASHBOARD_VIEW')) return '/dashboard';
  if (u.permissions.includes('ATTENDANCE_SELF')) return '/attendance';
  return '/notifications';
}

function Home() {
  const { user } = useAuth();
  return <Navigate to={user ? homeFor(user) : '/login'} replace />;
}

const has = (...p: string[]) => (u: Me) => p.some((x) => u.permissions.includes(x));

function ThemedToaster() {
  const { theme } = useTheme();
  return <Toaster position="bottom-right" theme={theme === 'DARK' ? 'dark' : 'light'} richColors closeButton />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<div className="p-8"><Loading /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route index element={<Home />} />
          <Route path="dashboard" element={<Guard allow={has('DASHBOARD_VIEW')}><DashboardPage /></Guard>} />
          <Route path="tickets" element={<Guard allow={has('TICKET_READ')}><TicketsPage /></Guard>} />
          <Route path="tickets/:id" element={<Guard allow={has('TICKET_READ')}><TicketDetailPage /></Guard>} />
          <Route path="attendance" element={<Guard allow={has('ATTENDANCE_SELF')}><AttendancePage /></Guard>} />
          <Route path="attendance/team" element={<Guard allow={(u) => has('ATTENDANCE_TEAM_VIEW', 'ATTENDANCE_ORG_VIEW')(u) || u.projectLead}><TeamAttendancePage /></Guard>} />
          <Route path="clients" element={<Guard allow={has('CLIENT_READ')}><ClientsPage /></Guard>} />
          <Route path="projects" element={<Guard allow={has('PROJECT_READ')}><ProjectsPage /></Guard>} />
          <Route path="projects/:id" element={<Guard allow={has('PROJECT_READ')}><ProjectDetailPage /></Guard>} />
          <Route path="people" element={<Guard allow={has('USER_READ', 'TEAM_READ')}><PeoplePage /></Guard>} />
          <Route path="sla" element={<Guard allow={has('SLA_READ')}><SlaPage /></Guard>} />
          <Route path="reports" element={<Guard allow={(u) => has('REPORT_VIEW', 'ATTENDANCE_REPORT_VIEW')(u) || u.projectLead}><ReportsPage /></Guard>} />
          <Route path="knowledge" element={<Guard allow={has('KB_READ')}><KnowledgePage /></Guard>} />
          <Route path="automation" element={<Guard allow={has('AUTOMATION_MANAGE')}><AutomationPage /></Guard>} />
          <Route path="integrations" element={<Guard allow={has('INTEGRATION_MANAGE')}><IntegrationsPage /></Guard>} />
          <Route path="security" element={<Guard allow={has('SECURITY_VIEW')}><SecurityPage /></Guard>} />
          <Route path="audit" element={<Guard allow={has('AUDIT_VIEW')}><AuditPage /></Guard>} />
          <Route path="settings" element={<Guard allow={has('SETTINGS_MANAGE', 'ATTENDANCE_MANAGE', 'ROLE_MANAGE')}><SettingsPage /></Guard>} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="*" element={<div className="py-24 text-center"><h1 className="text-xl font-semibold">Page not found</h1><p className="mt-2 text-sm text-muted-foreground">Check the address or use the menu to find what you need.</p></div>} />
        </Route>
      </Routes>
    </Suspense>
  );
}

export function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <TooltipProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
          <ThemedToaster />
        </TooltipProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
