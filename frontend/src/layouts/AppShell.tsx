import { AnimatePresence, motion } from 'motion/react';
import { Bell, LogOut, Menu, Moon, Sun, UserRound, X } from 'lucide-react';
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

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <nav aria-label="Main" className="flex h-full flex-col bg-navy text-white/80">
      <div className="flex h-16 items-center gap-2.5 px-5">
        <img src="/favicon.svg" alt="" className="h-8 w-8" />
        <div className="leading-tight">
          <p className="text-[15px] font-semibold text-white">SUMA</p>
          <p className="text-[11px] text-white/50">Tracking tool</p>
        </div>
      </div>
      <div className="flex-1 space-y-5 overflow-y-auto px-3 pb-6 pt-2">
        {visibleNav(user).map((g) => (
          <div key={g.label}>
            <p className="px-2 pb-1.5 text-[11px] font-medium text-white/40">{g.label}</p>
            <ul className="space-y-0.5">
              {g.items.map((i) => (
                <li key={i.to}>
                  <NavLink to={i.to} end={i.to === '/attendance'} onClick={onNavigate}
                    className={({ isActive }) => cn('group relative flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors',
                      isActive ? 'bg-white/10 text-white' : 'hover:bg-white/5 hover:text-white')}>
                    {({ isActive }) => (
                      <>
                        {isActive && <motion.span layoutId="nav-active" className="absolute inset-y-1.5 left-0 w-[3px] rounded-full bg-[#7FA8FF]" />}
                        <i.icon className="h-4 w-4 shrink-0" aria-hidden />
                        {i.label}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-white/10 px-5 py-3 text-[11px] text-white/40">Signed in as {titleCase(user.role)}{user.projectLead ? ' · Project Lead' : ''}</div>
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
        <Button variant="ghost" size="icon" aria-label={`Notifications${count ? `, ${count} unread` : ''}`} className="relative">
          <Bell />
          <AnimatePresence>
            {count > 0 && (
              <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}
                className="num absolute right-1 top-1 min-w-[16px] rounded-full bg-breach px-1 text-[10px] font-semibold leading-4 text-white">{count > 99 ? '99+' : count}</motion.span>
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
  useEffect(() => setMobile(false), [location.pathname]);
  if (!user) return null;

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

  return (
    <AttendanceProvider>
      <div className="flex min-h-screen">
        <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 lg:block"><Sidebar /></aside>
        <AnimatePresence>
          {mobile && (
            <>
              <motion.div className="fixed inset-0 z-40 bg-navy-900/50 lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setMobile(false)} />
              <motion.aside className="fixed inset-y-0 left-0 z-50 w-64 lg:hidden" initial={{ x: -280 }} animate={{ x: 0 }} exit={{ x: -280 }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
                <button className="absolute right-3 top-4 z-10 rounded p-1 text-white/70 hover:text-white" onClick={() => setMobile(false)} aria-label="Close menu"><X className="h-5 w-5" /></button>
                <Sidebar onNavigate={() => setMobile(false)} />
              </motion.aside>
            </>
          )}
        </AnimatePresence>
        <div className="flex min-w-0 flex-1 flex-col lg:pl-60">
          <header className="glass sticky top-0 z-20 flex h-14 items-center gap-2 rounded-none border-x-0 border-t-0 px-4 sm:px-6">
            <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMobile(true)} aria-label="Open menu"><Menu /></Button>
            <div className="flex-1" />
            {can('ATTENDANCE_SELF') && <BreakPill />}
            <NotificationsMenu />
            <Button variant="ghost" size="icon" onClick={switchTheme} aria-label={theme === 'DARK' ? 'Switch to light mode' : 'Switch to dark mode'}>
              {theme === 'DARK' ? <Sun /> : <Moon />}
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-2 rounded-full py-1 pl-1 pr-2 hover:bg-accent" aria-label="Account menu">
                  <Avatar name={user.fullName} />
                  <span className="hidden text-sm font-medium md:inline">{user.fullName}</span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel>{user.email}<br />{user.employeeCode}</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => navigate('/profile')}><UserRound /> Profile & password</DropdownMenuItem>
                <DropdownMenuItem onSelect={signOut} className="text-breach"><LogOut /> Sign out</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </header>
          <main className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
            <AnimatePresence mode="wait">
              <motion.div key={location.pathname} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.18 }}>
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>
      </div>
      {can('ATTENDANCE_SELF') && <ActivityMonitor />}
    </AttendanceProvider>
  );
}
