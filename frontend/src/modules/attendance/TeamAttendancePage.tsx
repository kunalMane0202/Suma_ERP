import { useState } from 'react';
import { motion } from 'motion/react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Bell, CheckCircle2, Clock, Coffee, Download, FileSpreadsheet, FileText, LogIn, LogOut, Mail, MailCheck, MailX, Moon, PenLine, RefreshCw, Send } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { FeedItem, IconTile, KeyValue, gridItem, gridParent } from '@/components/layout';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Avatar, Can, EmptyState, ErrorState, PageHeader, Pagination, Stat } from '@/components/common';
import { ShiftTimeline, TimelineLegend } from '@/components/ShiftTimeline';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, download, errorMessage } from '@/services/api';
import { CHART, tooltipStyle } from '@/utils/chart';
import { dateTime, duration, isoDate, relative, time, titleCase } from '@/utils/format';
import type { AttendanceDashboard, DailyReport, MonthlyReport, ReportLog } from '@/types/attendance';
import type { Page } from '@/types/api';

const statusBar: Record<string, string> = { WORKING: 'bg-ok', ON_BREAK: 'bg-warn', IDLE: 'bg-idle', AWAY: 'bg-muted-foreground/50', NOT_LOGGED_IN: 'bg-breach/60', LOGGED_OUT: 'bg-muted-foreground/30' };
const liveTone: Record<string, BadgeTone> = { WORKING: 'green', ON_BREAK: 'amber', IDLE: 'violet', AWAY: 'neutral', LOGGED_OUT: 'neutral', NOT_LOGGED_IN: 'red', WEEKLY_OFF: 'neutral', OFFLINE: 'neutral' };

function Filters({ value, onChange }: { value: Record<string, string | undefined>; onChange: (v: Record<string, string | undefined>) => void }) {
  const lk = useLookups();
  return (
    <>
      {lk.projects.length > 0 && (
        <NativeSelect aria-label="Project" className="h-9 w-44 rounded-full text-xs" value={value.projectId ?? ''} onChange={(e) => onChange({ ...value, projectId: e.target.value || undefined })}>
          <option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </NativeSelect>
      )}
      {lk.teams.length > 0 && (
        <NativeSelect aria-label="Team" className="h-9 w-44 rounded-full text-xs" value={value.teamId ?? ''} onChange={(e) => onChange({ ...value, teamId: e.target.value || undefined })}>
          <option value="">All teams</option>{lk.teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </NativeSelect>
      )}
      {lk.users.length > 1 && (
        <NativeSelect aria-label="Employee" className="h-9 w-44 rounded-full text-xs" value={value.userId ?? ''} onChange={(e) => onChange({ ...value, userId: e.target.value || undefined })}>
          <option value="">All employees</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
        </NativeSelect>
      )}
    </>
  );
}

const clean = (o: Record<string, string | undefined>) => Object.fromEntries(Object.entries(o).filter(([, v]) => v));

export default function TeamAttendancePage() {
  const { user, can } = useAuth();
  const isLeadOnly = !!user?.projectLead && !can('ATTENDANCE_TEAM_VIEW') && !can('ATTENDANCE_ORG_VIEW');
  return (
    <>
      <PageHeader title="Team attendance"
        description={isLeadOnly ? 'Attendance for the projects you lead, including the monthly reports emailed to you.' : 'Who is working, on break or inactive, with daily and monthly attendance records for your scope.'} />
      <Tabs defaultValue="live">
        <TabsList>
          <TabsTrigger value="live">Live</TabsTrigger>
          <TabsTrigger value="daily">Daily report</TabsTrigger>
          <TabsTrigger value="monthly">Monthly report</TabsTrigger>
          <TabsTrigger value="delivery">Project Lead reports</TabsTrigger>
          <TabsTrigger value="history">History</TabsTrigger>
        </TabsList>
        <TabsContent value="live"><LiveTab /></TabsContent>
        <TabsContent value="daily"><DailyTab /></TabsContent>
        <TabsContent value="monthly"><MonthlyTab /></TabsContent>
        <TabsContent value="delivery"><DeliveryTab /></TabsContent>
        <TabsContent value="history"><HistoryTab /></TabsContent>
      </Tabs>
    </>
  );
}

function LiveTab() {
  const [f, setF] = useState<Record<string, string | undefined>>({});
  const [view, setView] = useState<'cards' | 'table'>('cards');
  const { data, error, loading, reload } = useApi<AttendanceDashboard>('/attendance/summary', clean(f), { interval: 30_000 });
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2"><Filters value={f} onChange={setF} /><Button variant="ghost" size="sm" onClick={reload}><RefreshCw /> Refresh</Button>
        <span className="text-xs text-muted-foreground">Updates every 30 seconds</span></div>
      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? <TableSkeleton /> : data && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6">
            <Stat index={0} label="Logged in today" value={data.loggedInToday} hint={`of ${data.employeesInScope} employees`} />
            <Stat index={1} label="Active now" value={data.currentlyActive} tone="ok" />
            <Stat index={2} label="On break" value={data.onBreak} tone="warn" />
            <Stat index={3} label="Idle" value={data.idle} tone="idle" hint={`${data.away} marked away`} />
            <Stat index={4} label="Logged out" value={data.loggedOut} />
            <Stat index={5} label="Missing logout" value={data.missingLogout} tone={data.missingLogout ? 'breach' : 'default'} />
            <Stat index={6} label="Breaks today" value={data.totalBreaksToday} hint={`Average ${duration(data.averageBreakSeconds)}`} />
            <Stat index={7} label="Over break limit" value={data.exceedingBreakLimits} tone={data.exceedingBreakLimits ? 'breach' : 'default'} />
            <Stat index={8} label="Attendance exceptions" value={data.attendanceExceptions} tone={data.attendanceExceptions ? 'warn' : 'default'} />
            <Stat index={9} label="Not logged in" value={data.notLoggedIn} />
            <Stat index={10} label="Compliance" value={data.compliancePercent} format={(n) => `${n.toFixed(1)}%`} tone={data.compliancePercent >= 90 ? 'ok' : 'warn'} />
          </div>
          <div className="grid gap-4 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader>
                <div><CardTitle>Employee status</CardTitle><CardDescription>{data.date} · times in {data.timezone}</CardDescription></div>
                <div className="flex rounded-full bg-muted p-1" role="group" aria-label="Layout">
                  {(['cards', 'table'] as const).map((v) => (
                    <button key={v} onClick={() => setView(v)} aria-pressed={view === v}
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${view === v ? 'bg-card text-foreground shadow-glass' : 'text-muted-foreground'}`}>{v === 'cards' ? 'Cards' : 'Table'}</button>
                  ))}
                </div>
              </CardHeader>
              <CardContent className={view === 'table' ? 'px-0' : ''}>
                {view === 'cards' ? (
                  <div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-3">
                    {data.live.map((r, i) => (
                      <motion.div key={r.userId} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(i, 12) * 0.03 }}
                        className={`relative overflow-hidden rounded-2xl bg-muted/60 p-4 ring-1 ring-transparent transition hover:bg-card hover:shadow-lift hover:ring-border ${r.breakExceeded ? 'ring-breach/40' : ''}`}>
                        <span className={`absolute inset-y-0 left-0 w-1 ${statusBar[r.status] ?? 'bg-muted-foreground/30'}`} />
                        <div className="flex items-center gap-3">
                          <div className="relative"><Avatar name={r.fullName} className="h-10 w-10" />
                            <span className={`absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5 rounded-full ring-2 ring-card ${statusBar[r.status] ?? 'bg-muted-foreground/40'}`} /></div>
                          <div className="min-w-0 flex-1"><p className="truncate font-semibold">{r.fullName}</p><p className="truncate text-xs text-muted-foreground">{r.employeeCode}{r.teamName ? ` · ${r.teamName}` : ''}</p></div>
                        </div>
                        <div className="mt-3 flex items-center justify-between">
                          <Badge tone={liveTone[r.status] ?? 'neutral'}>{r.status === 'IDLE' ? 'Idle' : titleCase(r.status)}</Badge>
                          <span className="text-xs text-muted-foreground">
                            {r.breakStartedAt ? `break since ${time(r.breakStartedAt, data.timezone)}` : r.idleSince ? `idle since ${time(r.idleSince, data.timezone)}` : r.loginAt ? `in at ${time(r.loginAt, data.timezone)}` : '—'}
                          </span>
                        </div>
                        <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                          <div className="rounded-lg bg-card/80 px-2.5 py-1.5"><p className="text-muted-foreground">Breaks</p><p className={`display-num ${r.breakExceeded ? 'text-breach' : ''}`}>{r.todayBreakCount} · {duration(r.todayBreakSeconds)}</p></div>
                          <div className="rounded-lg bg-card/80 px-2.5 py-1.5"><p className="text-muted-foreground">Last activity</p><p className="display-num">{r.lastActivityAt ? relative(r.lastActivityAt) : '—'}</p></div>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                ) : (
                  <Table>
                    <THead><TR><TH>Employee</TH><TH>Status</TH><TH>First sign-in</TH><TH>Last activity</TH><TH>Breaks today</TH><TH>Project · team</TH></TR></THead>
                    <TBody>
                      {data.live.map((r) => (
                        <TR key={r.userId}>
                          <TD><p className="font-medium">{r.fullName}</p><p className="text-xs text-muted-foreground">{r.employeeCode}</p></TD>
                          <TD><Badge tone={liveTone[r.status] ?? 'neutral'}>{r.status === 'IDLE' ? 'Idle' : titleCase(r.status)}</Badge></TD>
                          <TD className="num">{time(r.loginAt, data.timezone)}</TD>
                          <TD className="text-xs text-muted-foreground">{r.lastActivityAt ? relative(r.lastActivityAt) : '—'}</TD>
                          <TD className={`num ${r.breakExceeded ? 'text-breach' : ''}`}>{r.todayBreakCount} · {duration(r.todayBreakSeconds)}</TD>
                          <TD className="text-xs text-muted-foreground">{r.projectName ?? '—'}{r.teamName ? ` · ${r.teamName}` : ''}</TD>
                        </TR>
                      ))}
                    </TBody>
                  </Table>
                )}
                {data.live.length === 0 && <EmptyState title="No employees in this scope" />}
              </CardContent>
            </Card>
            <Card>
              <CardHeader><div><CardTitle>Sign-ins and breaks by hour</CardTitle><CardDescription>Today, reporting timezone</CardDescription></div></CardHeader>
              <CardContent className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={data.loginsByHour.filter((h) => h.hour >= 5 && h.hour <= 23)} margin={{ left: -24, right: 4 }}>
                    <CartesianGrid stroke={CHART.grid} strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="hour" tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} />
                    <RTooltip {...tooltipStyle} labelFormatter={(h) => `${h}:00`} />
                    <Bar dataKey="logins" name="Sign-ins" fill={CHART.primary} radius={[6, 6, 6, 6]} barSize={10} />
                    <Bar dataKey="breaks" name="Breaks started" fill={CHART.warn} radius={[6, 6, 6, 6]} barSize={10} />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}

function ExportButtons({ params }: { params: Record<string, unknown> }) {
  const run = async (format: string) => {
    try {
      await download('/attendance/reports/export', { ...params, format });
    } catch (e) {
      toast.error(errorMessage(e, 'Export failed'));
    }
  };
  return (
    <div className="flex gap-1.5">
      <Button variant="outline" size="sm" onClick={() => run('xlsx')}><FileSpreadsheet /> Excel</Button>
      <Button variant="outline" size="sm" onClick={() => run('csv')}><Download /> CSV</Button>
      <Button variant="outline" size="sm" onClick={() => run('pdf')}><FileText /> PDF</Button>
    </div>
  );
}

function DailyTab() {
  const [day, setDay] = useState(isoDate(new Date()));
  const [f, setF] = useState<Record<string, string | undefined>>({});
  const { data, error, loading, reload } = useApi<DailyReport>('/attendance/reports/daily', { date: day, ...clean(f) });
  return (
    <div className="space-y-4">
    {data && <SummaryTiles items={[
      { label: 'Signed in', value: `${data.totals.present}/${data.totals.employees}`, bar: data.totals.employees ? data.totals.present / data.totals.employees : 0, tone: 'bg-ok' },
      { label: 'Session time', value: duration(data.totals.sessionSeconds) },
      { label: 'Breaks', value: `${data.totals.breakCount} · ${duration(data.totals.breakSeconds)}` },
      { label: 'Exceptions', value: String(data.totals.exceptions), alert: data.totals.exceptions > 0 },
    ]} />}
    <Card>
      <CardHeader className="flex-wrap">
        <div className="flex flex-wrap items-center gap-2">
          <Input type="date" aria-label="Date" className="h-9 w-40 rounded-full text-xs" value={day} onChange={(e) => e.target.value && setDay(e.target.value)} />
          <Filters value={f} onChange={setF} />
        </div>
        <ExportButtons params={{ type: 'daily', date: day, ...clean(f) }} />
      </CardHeader>
      <CardContent className="px-0">
        <div className="px-5 pb-3"><TimelineLegend /></div>
        {error && <div className="px-5"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (
          <>
            <Table>
              <THead><TR><TH>Employee</TH><TH>Sign-in</TH><TH>Sign-out</TH><TH className="min-w-[260px]">Day</TH><TH>Session</TH><TH>Breaks</TH><TH>Idle</TH><TH>Exceptions</TH></TR></THead>
              <TBody>
                {data.rows.map(({ employee: e, day: d }) => (
                  <TR key={e.userId}>
                    <TD><div className="flex items-center gap-3"><Avatar name={e.fullName} className="h-8 w-8 text-[10px]" /><div><p className="font-semibold">{e.fullName}</p><p className="text-xs text-muted-foreground">{e.employeeCode}{e.projectName ? ` · ${e.projectName}` : ''}{e.teamName ? ` · ${e.teamName}` : ''}</p></div></div></TD>
                    <TD className="num">{time(d.firstLoginAt, data.timezone)}</TD>
                    <TD className="num">{d.sessions.some((s) => s.ongoing) ? <Badge tone="green">Active</Badge> : time(d.lastLogoutAt, data.timezone)}</TD>
                    <TD>{d.present ? <ShiftTimeline compact data={{ date: d.date, timezone: data.timezone, sessions: d.sessions, breaks: d.breaks, idles: d.idles }} /> : <span className="text-xs text-muted-foreground">{d.weeklyOff ? 'Weekly off' : 'No sign-in'}</span>}</TD>
                    <TD className="num">{duration(d.sessionSeconds)}</TD>
                    <TD className="num">{d.breakCount} · {duration(d.breakSeconds)}</TD>
                    <TD className="num">{d.idleCount ? `${d.idleCount} · ${duration(d.idleSeconds)}` : '—'}</TD>
                    <TD><div className="flex flex-wrap gap-1">{d.exceptions.map((x) => <Badge key={x} tone={x === 'MISSING_LOGOUT' || x.includes('EXCEEDED') ? 'red' : 'amber'}>{titleCase(x)}</Badge>)}</div></TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            {data.rows.length === 0 && <EmptyState title="No employees match these filters" />}
          </>
        )}
      </CardContent>
    </Card>
    </div>
  );
}

function SummaryTiles({ items }: { items: { label: string; value: string; bar?: number; tone?: string; alert?: boolean }[] }) {
  return (
    <motion.div className="grid grid-cols-2 gap-3 lg:grid-cols-4" initial="hidden" animate="show" variants={gridParent}>
      {items.map((i) => (
        <motion.div key={i.label} variants={gridItem} className="glass p-4">
          <p className="text-xs text-muted-foreground">{i.label}</p>
          <p className={`display-num mt-1 text-2xl ${i.alert ? 'text-breach' : ''}`}>{i.value}</p>
          {i.bar !== undefined && <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted"><div className={`h-full rounded-full ${i.tone ?? 'bg-brand'}`} style={{ width: `${Math.round(i.bar * 100)}%` }} /></div>}
        </motion.div>
      ))}
    </motion.div>
  );
}

function MonthlyTab() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [y, m] = month.split('-').map(Number);
  const [f, setF] = useState<Record<string, string | undefined>>({});
  const { data, error, loading, reload } = useApi<MonthlyReport>('/attendance/reports/monthly', { year: y, month: m, ...clean(f) });
  const emps = data?.employees ?? [];
  const maxDays = Math.max(1, ...emps.map((x) => x.daysPresent + x.missingLoginDays));
  const sum = (k: (x: (typeof emps)[number]) => number) => emps.reduce((a, x) => a + k(x), 0);
  return (
    <div className="space-y-4">
    {data && <SummaryTiles items={[
      { label: 'Employees', value: String(emps.length) },
      { label: 'Session time', value: duration(sum((x) => x.sessionSeconds)) },
      { label: 'Breaks over limit', value: String(sum((x) => x.exceededBreakCount)), alert: sum((x) => x.exceededBreakCount) > 0 },
      { label: 'Missing logouts', value: String(sum((x) => x.missingLogoutCount)), alert: sum((x) => x.missingLogoutCount) > 0 },
    ]} />}
    <Card>
      <CardHeader className="flex-wrap">
        <div className="flex flex-wrap items-center gap-2">
          <Input type="month" aria-label="Month" className="h-9 w-40 rounded-full text-xs" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />
          <Filters value={f} onChange={setF} />
        </div>
        <ExportButtons params={{ type: 'monthly', year: y, month: m, ...clean(f) }} />
      </CardHeader>
      <CardContent className="px-0">
        {error && <div className="px-5"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (
          <>
            <Table>
              <THead><TR><TH>Employee</TH><TH className="text-right">Days present</TH><TH className="text-right">Session time</TH><TH className="text-right">Breaks</TH><TH className="text-right">Break time</TH><TH className="text-right">Avg break</TH><TH className="text-right">Over limit</TH><TH className="text-right">Idle events</TH><TH className="text-right">Missing logout</TH><TH className="text-right">No sign-in</TH><TH className="text-right">Exceptions</TH></TR></THead>
              <TBody>
                {data.employees.map((x) => (
                  <TR key={x.employee.userId}>
                    <TD><div className="flex items-center gap-3"><Avatar name={x.employee.fullName} className="h-8 w-8 text-[10px]" /><div><p className="font-semibold">{x.employee.fullName}</p><p className="text-xs text-muted-foreground">{x.employee.employeeCode}{x.employee.projectName ? ` · ${x.employee.projectName}` : ''}</p></div></div></TD>
                    <TD className="num text-right"><div className="flex items-center justify-end gap-2"><div className="hidden h-1.5 w-16 overflow-hidden rounded-full bg-muted sm:block"><div className="h-full rounded-full bg-brand" style={{ width: `${(x.daysPresent / maxDays) * 100}%` }} /></div>{x.daysPresent}</div></TD><TD className="num text-right">{duration(x.sessionSeconds)}</TD><TD className="num text-right">{x.breakCount}</TD>
                    <TD className="num text-right">{duration(x.breakSeconds)}</TD><TD className="num text-right">{duration(x.averageBreakSeconds)}</TD>
                    <TD className={`num text-right ${x.exceededBreakCount ? 'text-breach' : ''}`}>{x.exceededBreakCount}</TD>
                    <TD className="num text-right">{x.idleCount}</TD><TD className={`num text-right ${x.missingLogoutCount ? 'text-breach' : ''}`}>{x.missingLogoutCount}</TD>
                    <TD className="num text-right">{x.missingLoginDays}</TD><TD className="num text-right">{x.exceptionCount}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            {data.employees.length === 0 && <EmptyState title="No attendance records for this month" />}
            <p className="px-5 pt-3 text-xs text-muted-foreground">Reporting timezone {data.timezone}. Excel export includes daily details, every break timing and idle events.</p>
          </>
        )}
      </CardContent>
    </Card>
    </div>
  );
}

const deliveryTone: Record<string, BadgeTone> = { SENT: 'green', GENERATED: 'blue', PENDING: 'blue', RETRYING: 'amber', FAILED: 'red', SKIPPED_UNVERIFIED: 'amber', SKIPPED_NO_RECIPIENT: 'amber' };

function DeliveryTab() {
  const lk = useLookups();
  const { can } = useAuth();
  const { data, error, loading, reload } = useApi<ReportLog[]>('/attendance/monthly-reports');
  const [open, setOpen] = useState(false);
  const last = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  const [form, setForm] = useState({ projectId: '', month: `${last.getFullYear()}-${String(last.getMonth() + 1).padStart(2, '0')}` });
  const [busy, setBusy] = useState(false);
  const projectName = (id: string) => lk.projects.find((p) => p.id === id)?.name ?? id.slice(0, 8);
  const generate = async () => {
    setBusy(true);
    try {
      const [y, m] = form.month.split('-').map(Number);
      await api.post('/attendance/monthly-reports', { projectId: form.projectId, year: y, month: m });
      toast.success('Report generated and queued for email');
      setOpen(false);
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const resend = async (l: ReportLog) => {
    const force = l.status === 'SENT';
    if (force && !window.confirm('This report was already delivered. Send it again?')) return;
    try {
      await api.post(`/attendance/monthly-reports/${l.id}/send`, null, { params: { force } });
      toast.success('Report queued for sending');
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  return (
    <Card>
      <CardHeader>
        <div><CardTitle>Monthly reports to Project Leads</CardTitle>
          <CardDescription>Generated automatically on the 1st of each month for the previous month, once per project, and emailed only to verified Project Lead addresses.</CardDescription></div>
        <Can permission="ATTENDANCE_MANAGE"><Button size="sm" onClick={() => setOpen(true)}><Mail /> Send a report</Button></Can>
      </CardHeader>
      <CardContent className="px-0">
        {error && <div className="px-5"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : (data?.length ?? 0) === 0 ? (
          <EmptyState icon={<Mail />} title="No monthly reports yet" description="The first report is generated on the 1st of next month. Administrators can also send one for a completed month." />
        ) : (
          <div className="grid gap-3 px-5 sm:grid-cols-2 xl:grid-cols-3">
            {data!.map((l) => (
              <div key={l.id} className="flex flex-col rounded-2xl border border-border/60 bg-muted/40 p-4">
                <div className="flex items-start gap-3">
                  <IconTile icon={l.status === 'SENT' ? MailCheck : l.status === 'FAILED' ? MailX : Mail} tone={l.status === 'SENT' ? 'mint' : l.status === 'FAILED' ? 'coral' : 'soft'} />
                  <div className="min-w-0 flex-1"><p className="truncate font-semibold">{projectName(l.projectId)}</p>
                    <p className="display-num text-xs text-muted-foreground">{new Date(l.periodYear, l.periodMonth - 1, 1).toLocaleString(undefined, { month: 'long', year: 'numeric' })}</p></div>
                  <Badge tone={deliveryTone[l.status] ?? 'neutral'}>{titleCase(l.status)}</Badge>
                </div>
                <KeyValue className="mt-3 bg-card/70" items={[
                  ['Recipient', <span className="block max-w-[11rem] truncate text-xs" title={l.recipientEmail}>{l.recipientEmail ?? '—'}</span>],
                  ['Employees', l.employeeCount], ['Attempts', l.attempts],
                  [l.sentAt ? 'Sent' : 'Next retry', l.sentAt ? dateTime(l.sentAt) : l.nextRetryAt ? relative(l.nextRetryAt) : '—'],
                ]} />
                {l.lastError && <p className="mt-2 truncate rounded-lg bg-breach/10 px-2 py-1 text-xs text-breach" title={l.lastError}>{l.lastError}</p>}
                <div className="mt-3 flex justify-end gap-1">
                  <Button variant="ghost" size="sm" onClick={() => download(`/attendance/monthly-reports/${l.id}/download`, { format: 'xlsx' }).catch((e) => toast.error(errorMessage(e)))}><Download /> Excel</Button>
                  {can('ATTENDANCE_MANAGE') && <Button variant="ghost" size="sm" onClick={() => resend(l)}><Send /> {l.status === 'SENT' ? 'Resend' : 'Send'}</Button>}
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
      <Dialog open={open} onOpenChange={setOpen} title="Send a monthly attendance report" description="Only completed months can be sent. A period that was already generated cannot be generated twice; use Resend instead."
        footer={<><Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button><Button loading={busy} disabled={!form.projectId} onClick={generate}>Generate and send</Button></>}>
        <div className="space-y-4">
          <Field label="Project" htmlFor="mr-project">
            <NativeSelect id="mr-project" value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value })}>
              <option value="">Choose a project</option>
              {lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}{p.projectLeadEmail ? ` — ${p.projectLeadEmail}${p.projectLeadEmailVerified ? '' : ' (unverified)'}` : ' — no Project Lead email'}</option>)}
            </NativeSelect>
          </Field>
          <Field label="Month" htmlFor="mr-month"><Input id="mr-month" type="month" value={form.month} onChange={(e) => setForm({ ...form, month: e.target.value })} /></Field>
        </div>
      </Dialog>
    </Card>
  );
}

const EVENT_ICON: Record<string, [LucideIcon, string]> = {
  LOGIN: [LogIn, 'bg-ok/10 text-ok'], LOGOUT: [LogOut, 'bg-muted text-muted-foreground'], BREAK_START: [Coffee, 'bg-warn/15 text-warn'], BREAK_END: [Coffee, 'bg-warn/10 text-warn'],
  IDLE_START: [Moon, 'bg-idle/15 text-idle'], IDLE_END: [Moon, 'bg-idle/10 text-idle'], STATUS_CONFIRMED: [CheckCircle2, 'bg-ok/10 text-ok'], REMINDER_SENT: [Bell, 'bg-sky/15 text-sky'], CORRECTION: [PenLine, 'bg-breach/10 text-breach'],
};

interface EventRow { id: string; userId: string; userName?: string; eventType: string; occurredAt: string; source: string; details?: string }

function HistoryTab() {
  const { can } = useAuth();
  const lk = useLookups();
  const [page, setPage] = useState(0);
  const [f, setF] = useState<{ userId?: string; type?: string; from?: string; to?: string }>({});
  const { data, error, loading, reload } = useApi<Page<EventRow>>('/attendance/events', { page, size: 50, ...clean(f) });
  const [corr, setCorr] = useState(false);
  return (
    <Card>
      <CardHeader className="flex-wrap">
        <div className="flex flex-wrap items-center gap-2">
          {lk.users.length > 1 && <NativeSelect aria-label="Employee" className="h-9 w-44 rounded-full text-xs" value={f.userId ?? ''} onChange={(e) => { setPage(0); setF({ ...f, userId: e.target.value || undefined }); }}><option value="">All employees</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect>}
          <NativeSelect aria-label="Event type" className="h-9 w-44 rounded-full text-xs" value={f.type ?? ''} onChange={(e) => { setPage(0); setF({ ...f, type: e.target.value || undefined }); }}>
            <option value="">All events</option>
            {['LOGIN', 'LOGOUT', 'BREAK_START', 'BREAK_END', 'IDLE_START', 'IDLE_END', 'STATUS_CONFIRMED', 'REMINDER_SENT', 'CORRECTION'].map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}
          </NativeSelect>
          <Input type="date" aria-label="From" className="h-9 w-40 rounded-full text-xs" value={f.from ?? ''} onChange={(e) => setF({ ...f, from: e.target.value || undefined })} />
          <Input type="date" aria-label="To" className="h-9 w-40 rounded-full text-xs" value={f.to ?? ''} onChange={(e) => setF({ ...f, to: e.target.value || undefined })} />
        </div>
        {can('ATTENDANCE_MANAGE') && <Button size="sm" variant="outline" onClick={() => setCorr(true)}>Correct a record</Button>}
      </CardHeader>
      <CardContent className="px-0">
        {error && <div className="px-5"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (
          <>
            <ul className="space-y-0.5 px-2">
              {data.content.map((e) => {
                const [Icon, tone] = EVENT_ICON[e.eventType] ?? [Clock, 'bg-primary/10 text-primary'];
                return (
                  <FeedItem key={e.id} icon={Icon} tone={tone}
                    title={<><span className="font-semibold">{e.userName}</span> <span className="text-muted-foreground">·</span> {titleCase(e.eventType)}</>}
                    meta={<>{titleCase(e.source)}{e.details ? ` · ${e.details}` : ''}</>}
                    right={<span className="display-num whitespace-nowrap text-xs text-muted-foreground">{dateTime(e.occurredAt)}</span>} />
                );
              })}
            </ul>
            {data.content.length === 0 && <EmptyState title="No attendance events for these filters" />}
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        )}
      </CardContent>
      <CorrectionDialog open={corr} onOpenChange={setCorr} onDone={reload} />
    </Card>
  );
}

function CorrectionDialog({ open, onOpenChange, onDone }: { open: boolean; onOpenChange: (o: boolean) => void; onDone: () => void }) {
  const lk = useLookups();
  const [userId, setUserId] = useState('');
  const [day, setDay] = useState(isoDate(new Date(Date.now() - 86400000)));
  const sessions = useApi<{ id: string; loginAt: string; logoutAt?: string; status: string }[]>(userId ? '/attendance/sessions' : null, { userId, from: day, to: day });
  const breaks = useApi<{ id: string; startedAt: string; endedAt?: string; category: string; status: string }[]>(userId ? '/attendance/breaks/history' : null, { userId, from: day, to: day });
  const [target, setTarget] = useState('');
  const [field, setField] = useState('logoutAt');
  const [value, setValue] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [type, id] = target.split(':');
  const submit = async () => {
    setBusy(true);
    try {
      await api.post('/attendance/corrections', { targetType: type, targetId: id, field, value: field === 'breakCategory' ? value : new Date(value).toISOString(), reason });
      toast.success('Correction saved. The original value is kept in the audit trail.');
      onOpenChange(false);
      onDone();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Correct an attendance record" description="Corrections never overwrite history: the original value, your reason and your name are recorded and the employee is notified."
      footer={<><Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button><Button loading={busy} disabled={!target || !value || reason.length < 5} onClick={submit}>Save correction</Button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Employee" htmlFor="c-user"><NativeSelect id="c-user" value={userId} onChange={(e) => setUserId(e.target.value)}><option value="">Choose</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect></Field>
        <Field label="Date" htmlFor="c-day"><Input id="c-day" type="date" value={day} onChange={(e) => setDay(e.target.value)} /></Field>
        <Field label="Record" htmlFor="c-target" className="sm:col-span-2">
          <NativeSelect id="c-target" value={target} onChange={(e) => { setTarget(e.target.value); setField(e.target.value.startsWith('SESSION') ? 'logoutAt' : 'endedAt'); }}>
            <option value="">Choose a session or break</option>
            {(sessions.data ?? []).map((s) => <option key={s.id} value={`SESSION:${s.id}`}>Session {time(s.loginAt)}–{s.logoutAt ? time(s.logoutAt) : 'active'} ({titleCase(s.status)})</option>)}
            {(breaks.data ?? []).map((b) => <option key={b.id} value={`BREAK:${b.id}`}>{titleCase(b.category)} break {time(b.startedAt)}–{b.endedAt ? time(b.endedAt) : 'active'}</option>)}
          </NativeSelect>
        </Field>
        <Field label="Field" htmlFor="c-field">
          <NativeSelect id="c-field" value={field} onChange={(e) => setField(e.target.value)}>
            {type === 'SESSION' ? <><option value="logoutAt">Sign-out time</option><option value="loginAt">Sign-in time</option></> : <><option value="endedAt">Break end</option><option value="startedAt">Break start</option><option value="breakCategory">Break type</option></>}
          </NativeSelect>
        </Field>
        <Field label="Corrected value" htmlFor="c-value">
          {field === 'breakCategory' ? <NativeSelect id="c-value" value={value} onChange={(e) => setValue(e.target.value)}><option value="">Choose</option>{['GENERAL', 'MEAL', 'PERSONAL', 'MEETING'].map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}</NativeSelect>
            : <Input id="c-value" type="datetime-local" value={value} onChange={(e) => setValue(e.target.value)} />}
        </Field>
        <Field label="Reason" htmlFor="c-reason" className="sm:col-span-2" hint="At least 5 characters. Visible to the employee."><Textarea id="c-reason" value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      </div>
    </Dialog>
  );
}
