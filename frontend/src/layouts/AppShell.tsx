import { AnimatePresence, motion } from 'motion/react';
import { Bell, LogOut, Menu, Moon, PanelLeftClose, PanelLeftOpen, Search, Sun, UserRound, X } from 'lucide-react';
import { CommandPalette } from './CommandPalette';
import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown';
import { Avatar } from '@/components/common';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { AttendanceProvider } from '@/contexts/AttendanceContext';
import { ActivityMonitor } from '@/modules/attendance/ActivityMonitor';
import { BreakPill } from '@/modules/attendance/BreakControl';
import { visibleNav } from '@/routes/nav';
import { api } from '@/services/api';
import { cn } from '@/utils/cn';
import { relative, titleCase } from '@/utils/format';

interface NotificationItem { id: string; title: string; message?: string; severity: string; readAt?: string; createdAt: string; entityType?: string; entityId?: string }

function Sidebar({ onNavigate, collapsed = false, onToggle }: { onNavigate?: () => void; collapsed?: boolean; onToggle?: () => void }) {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <nav aria-label="Main" className="glass flex h-full flex-col overflow-hidden">
      <div className={cn('flex h-[72px] items-center gap-3 px-5', collapsed && 'justify-center px-0')}>
        <img src="/favicon.svg" alt="" width={36} height={36} className="h-9 w-9 shrink-0 rounded-[10px] shadow-glow" />
        {!collapsed && (
          <div className="leading-tight">
            <p className="font-display text-[17px] font-bold tracking-tight">SUMA</p>
            <p className="text-[11px] text-muted-foreground">Tracking tool</p>
          </div>
        )}
      </div>
      <div className={cn('flex-1 space-y-5 overflow-y-auto pb-4 pt-1', collapsed ? 'px-2' : 'px-3')}>
        {visibleNav(user).map((g) => (
          <div key={g.label}>
            {!collapsed ? <p className="px-3 pb-1.5 text-[11px] font-semibold text-muted-foreground">{g.label}</p> : <div className="mx-3 mb-2 h-px bg-border" />}
            <ul className="space-y-0.5">
              {g.items.map((i) => (
                <li key={i.to}>
                  <NavLink to={i.to} end={i.to === '/attendance'} onClick={onNavigate} title={collapsed ? i.label : undefined}
                    aria-label={collapsed ? i.label : undefined}
                    className={({ isActive }) => cn('group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-[13.5px] font-medium transition-colors',
                      collapsed && 'justify-center px-0',
                      isActive ? 'text-white' : 'text-foreground/70 hover:bg-accent hover:text-foreground')}>
                    {({ isActive }) => (
                      <>
                        {isActive && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-xl bg-brand shadow-glow" transition={{ type: 'spring', stiffness: 380, damping: 32 }} />}
                        <i.icon className="relative h-[18px] w-[18px] shrink-0" aria-hidden />
                        {!collapsed && <span className="relative">{i.label}</span>}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      {onToggle && (
        <button onClick={onToggle} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="m-3 flex items-center justify-center gap-2 rounded-xl bg-muted py-2 text-xs font-medium text-muted-foreground hover:bg-accent hover:text-foreground">
          {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <><PanelLeftClose className="h-4 w-4" /> Collapse</>}
        </button>
      )}
    </nav>
  );
}

function NotificationsMenu() {
  const navigate = useNavigate();
  const [count, setCount] = useState(0);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const load = () => {
    api.get<{ count: number }>('/notifications/unread-count').then((r) => setCount(r.data.count)).catch(() => undefined);
  };
  useEffect(() => {
    load();
    const h = window.setInterval(load, 60_000);
    return () => window.clearInterval(h);
  }, []);
  const open = (o: boolean) => {
    if (o) api.get<{ content: NotificationItem[] }>('/notifications', { params: { size: 8 } }).then((r) => setItems(r.data.content)).catch(() => undefined);
  };
  const readAll = async () => {
    await api.post('/notifications/read-all');
    setCount(0);
    setItems((x) => x.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
  };
  return (
    <DropdownMenu onOpenChange={open}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Notifications${count ? `, ${count} unread` : ''}`} className="relative h-9 w-9">
          <Bell />
          <AnimatePresence>
            {count > 0 && (
              <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}
                className="num absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-breach px-1 text-[10px] font-bold leading-[18px] text-white ring-2 ring-card">{count > 99 ? '99+' : count}</motion.span>
            )}
          </AnimatePresence>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[22rem] p-0">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <p className="text-sm font-semibold">Notifications</p>
          {count > 0 && <button className="text-xs text-primary hover:underline" onClick={readAll}>Mark all read</button>}
        </div>
        <div className="max-h-96 overflow-y-auto">
          {items.length === 0 && <p className="px-3 py-6 text-center text-sm text-muted-foreground">You're all caught up.</p>}
          {items.map((n) => (
            <DropdownMenuItem key={n.id} className="flex-col items-start gap-0.5 rounded-none border-b px-3 py-2.5 last:border-0"
              onSelect={() => n.entityType === 'Ticket' && n.entityId ? navigate(`/tickets/${n.entityId}`) : navigate('/notifications')}>
              <div className="flex w-full items-center gap-2">
                {!n.readAt && <span className="h-1.5 w-1.5 rounded-full bg-primary" aria-label="Unread" />}
                <span className={cn('flex-1 text-sm font-medium', n.severity === 'CRITICAL' && 'text-breach', n.severity === 'WARNING' && 'text-warn')}>{n.title}</span>
                <span className="text-[11px] text-muted-foreground">{relative(n.createdAt)}</span>
              </div>
              {n.message && <p className="line-clamp-2 text-xs text-muted-foreground">{n.message}</p>}
            </DropdownMenuItem>
          ))}
        </div>
        <button onClick={() => navigate('/notifications')} className="w-full border-t px-3 py-2 text-center text-xs text-primary hover:bg-accent">See all notifications</button>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function AppShell() {
  const { user, logout, updateUser, can } = useAuth();
  const { theme, toggle } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobile, setMobile] = useState(false);
  const [palette, setPalette] = useState(false);
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('suma.sidebar') === 'collapsed'; } catch { return false; } });
  useEffect(() => setMobile(false), [location.pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPalette((p) => !p); } };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
  if (!user) return null;

  const toggleCollapsed = () => setCollapsed((c) => { const n = !c; try { localStorage.setItem('suma.sidebar', n ? 'collapsed' : 'open'); } catch { /* ignore */ } return n; });
  const switchTheme = async () => {
    const next = toggle();
    try {
      const r = await api.put('/auth/me/preferences', { themePreference: next });
      updateUser(r.data);
    } catch {
      /* preference stays local */
    }
  };
  const signOut = async () => {
    await logout();
    toast.success('Signed out');
    navigate('/login', { replace: true });
  };
  const side = collapsed ? 'lg:pl-[104px]' : 'lg:pl-[272px]';

  return (
    <AttendanceProvider>
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-full focus:bg-card focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:shadow-lift">Skip to main content</a>
      <div className="flex min-h-screen">
        <aside className={cn('fixed inset-y-3 left-3 z-30 hidden transition-[width] duration-300 lg:block', collapsed ? 'w-[80px]' : 'w-[248px]')}>
          <Sidebar collapsed={collapsed} onToggle={toggleCollapsed} />
        </aside>
        <AnimatePresence>
          {mobile && (
            <>
              <motion.div aria-hidden className="fixed inset-0 z-40 bg-navy-900/40 backdrop-blur-sm lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setMobile(false)} />
              <motion.aside className="fixed inset-y-3 left-3 z-50 w-[260px] lg:hidden" initial={{ x: -300 }} animate={{ x: 0 }} exit={{ x: -300 }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
                <button className="absolute right-3 top-5 z-10 rounded-full p-1.5 text-muted-foreground hover:bg-accent" onClick={() => setMobile(false)} aria-label="Close menu"><X className="h-5 w-5" /></button>
                <Sidebar onNavigate={() => setMobile(false)} />
              </motion.aside>
            </>
          )}
        </AnimatePresence>
        <div className={cn('flex min-w-0 flex-1 flex-col transition-[padding] duration-300', side)}>
          <header className="sticky top-0 z-20 flex h-[72px] items-center gap-2 bg-background/70 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
            <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMobile(true)} aria-label="Open menu"><Menu /></Button>
            <button onClick={() => setPalette(true)} aria-label="Search (Ctrl+K)"
              className="glass flex h-10 min-w-0 items-center gap-2.5 rounded-full px-4 text-sm text-muted-foreground transition-shadow hover:shadow-lift sm:w-72">
              <Search className="h-4 w-4 shrink-0" />
              <span className="hidden truncate sm:inline">Search pages and tickets</span>
              <kbd className="ml-auto hidden rounded-md bg-muted px-1.5 py-0.5 text-[11px] sm:inline">Ctrl K</kbd>
            </button>
            <div className="flex-1" />
            {can('ATTENDANCE_SELF') && <BreakPill />}
            <div className="glass flex items-center gap-0.5 rounded-full p-1">
              <NotificationsMenu />
              <Button variant="ghost" size="icon" className="h-9 w-9" onClick={switchTheme} aria-label={theme === 'DARK' ? 'Switch to light mode' : 'Switch to dark mode'}>
                {theme === 'DARK' ? <Sun /> : <Moon />}
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className="flex items-center gap-2 rounded-full py-0.5 pl-0.5 pr-3 hover:bg-accent" aria-label="Account menu">
                    <Avatar name={user.fullName} className="h-8 w-8" />
                    <span className="hidden text-left leading-tight md:block">
                      <span className="block text-[13px] font-semibold">{user.fullName}</span>
                      <span className="block text-[11px] text-muted-foreground">{titleCase(user.role)}{user.projectLead ? ' · Project Lead' : ''}</span>
                    </span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuLabel>{user.email}<br />{user.employeeCode}</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => navigate('/profile')}><UserRound /> Profile & password</DropdownMenuItem>
                  <DropdownMenuItem onSelect={signOut} className="text-breach"><LogOut /> Sign out</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </header>
          <main id="main" tabIndex={-1} className="mx-auto focus:outline-none w-full max-w-[1480px] flex-1 px-4 pb-10 pt-4 sm:px-6 lg:px-8">
            <AnimatePresence mode="wait">
              <motion.div key={location.pathname} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>
      </div>
      <CommandPalette open={palette} onOpenChange={setPalette} />
      {can('ATTENDANCE_SELF') && <ActivityMonitor />}
    </AttendanceProvider>
  );
}
