import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Download, FileSpreadsheet, FileText, Mail, RefreshCw, Send } from 'lucide-react';
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
import { Can, EmptyState, ErrorState, PageHeader, Pagination, Stat } from '@/components/common';
import { ShiftTimeline, TimelineLegend } from '@/components/ShiftTimeline';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, download, errorMessage } from '@/services/api';
import { CHART, tooltipStyle } from '@/utils/chart';
import { dateTime, duration, isoDate, relative, time, titleCase } from '@/utils/format';
import type { AttendanceDashboard, DailyReport, MonthlyReport, ReportLog } from '@/types/attendance';
import type { Page } from '@/types/api';

const liveTone: Record<string, BadgeTone> = { WORKING: 'green', ON_BREAK: 'amber', IDLE: 'violet', AWAY: 'neutral', LOGGED_OUT: 'neutral', NOT_LOGGED_IN: 'red', WEEKLY_OFF: 'neutral', OFFLINE: 'neutral' };

function Filters({ value, onChange }: { value: Record<string, string | undefined>; onChange: (v: Record<string, string | undefined>) => void }) {
  const lk = useLookups();
  return (
    <>
      {lk.projects.length > 0 && (
        <NativeSelect aria-label="Project" className="h-8 w-44 text-xs" value={value.projectId ?? ''} onChange={(e) => onChange({ ...value, projectId: e.target.value || undefined })}>
          <option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </NativeSelect>
      )}
      {lk.teams.length > 0 && (
        <NativeSelect aria-label="Team" className="h-8 w-44 text-xs" value={value.teamId ?? ''} onChange={(e) => onChange({ ...value, teamId: e.target.value || undefined })}>
          <option value="">All teams</option>{lk.teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </NativeSelect>
      )}
      {lk.users.length > 1 && (
        <NativeSelect aria-label="Employee" className="h-8 w-44 text-xs" value={value.userId ?? ''} onChange={(e) => onChange({ ...value, userId: e.target.value || undefined })}>
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
              <CardHeader><div><CardTitle>Employee status</CardTitle><CardDescription>{data.date} · times in {data.timezone}</CardDescription></div></CardHeader>
              <CardContent className="px-0">
                <Table>
                  <THead><TR><TH>Employee</TH><TH>Status</TH><TH>First sign-in</TH><TH>Last activity</TH><TH>Breaks today</TH><TH>Project · team</TH></TR></THead>
                  <TBody>
                    {data.live.map((r) => (
                      <TR key={r.userId}>
                        <TD><p className="font-medium">{r.fullName}</p><p className="text-xs text-muted-foreground">{r.employeeCode}</p></TD>
                        <TD>
                          <Badge tone={liveTone[r.status] ?? 'neutral'}>{r.status === 'IDLE' ? 'Idle' : titleCase(r.status)}</Badge>
                          {r.breakStartedAt && <p className="mt-0.5 text-xs text-muted-foreground">since {time(r.breakStartedAt, data.timezone)}</p>}
                          {r.idleSince && <p className="mt-0.5 text-xs text-muted-foreground">no activity since {time(r.idleSince, data.timezone)}</p>}
                        </TD>
                        <TD className="num">{time(r.loginAt, data.timezone)}</TD>
                        <TD className="text-xs text-muted-foreground">{r.lastActivityAt ? relative(r.lastActivityAt) : '—'}</TD>
                        <TD className={`num ${r.breakExceeded ? 'text-breach' : ''}`}>{r.todayBreakCount} · {duration(r.todayBreakSeconds)}</TD>
                        <TD className="text-xs text-muted-foreground">{r.projectName ?? '—'}{r.teamName ? ` · ${r.teamName}` : ''}</TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
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
                    <Bar dataKey="logins" name="Sign-ins" fill={CHART.primary} radius={[3, 3, 0, 0]} />
                    <Bar dataKey="breaks" name="Breaks started" fill={CHART.warn} radius={[3, 3, 0, 0]} />
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
    <Card>
      <CardHeader className="flex-wrap">
        <div className="flex flex-wrap items-center gap-2">
          <Input type="date" aria-label="Date" className="h-8 w-40 text-xs" value={day} onChange={(e) => e.target.value && setDay(e.target.value)} />
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
                    <TD><p className="font-medium">{e.fullName}</p><p className="text-xs text-muted-foreground">{e.employeeCode}{e.projectName ? ` · ${e.projectName}` : ''}{e.teamName ? ` · ${e.teamName}` : ''}</p></TD>
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
            <p className="px-5 pt-3 text-xs text-muted-foreground">
              {data.totals.employees} employees · {data.totals.present} signed in · {duration(data.totals.sessionSeconds)} total session time · {data.totals.breakCount} breaks ({duration(data.totals.breakSeconds)}) · {data.totals.exceptions} exceptions
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function MonthlyTab() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [y, m] = month.split('-').map(Number);
  const [f, setF] = useState<Record<string, string | undefined>>({});
  const { data, error, loading, reload } = useApi<MonthlyReport>('/attendance/reports/monthly', { year: y, month: m, ...clean(f) });
  return (
    <Card>
      <CardHeader className="flex-wrap">
        <div className="flex flex-wrap items-center gap-2">
          <Input type="month" aria-label="Month" className="h-8 w-40 text-xs" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />
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
                    <TD><p className="font-medium">{x.employee.fullName}</p><p className="text-xs text-muted-foreground">{x.employee.employeeCode}{x.employee.projectName ? ` · ${x.employee.projectName}` : ''}</p></TD>
                    <TD className="num text-right">{x.daysPresent}</TD><TD className="num text-right">{duration(x.sessionSeconds)}</TD><TD className="num text-right">{x.breakCount}</TD>
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
          <Table>
            <THead><TR><TH>Project</TH><TH>Period</TH><TH>Recipient</TH><TH>Status</TH><TH className="text-right">Employees</TH><TH className="text-right">Attempts</TH><TH>Sent / next retry</TH><TH /></TR></THead>
            <TBody>
              {data!.map((l) => (
                <TR key={l.id}>
                  <TD className="font-medium">{projectName(l.projectId)}</TD>
                  <TD className="num">{l.periodYear}-{String(l.periodMonth).padStart(2, '0')}</TD>
                  <TD className="text-xs">{l.recipientEmail ?? '—'}</TD>
                  <TD><Badge tone={deliveryTone[l.status] ?? 'neutral'}>{titleCase(l.status)}</Badge>{l.lastError && <p className="mt-1 max-w-xs truncate text-xs text-muted-foreground" title={l.lastError}>{l.lastError}</p>}</TD>
                  <TD className="num text-right">{l.employeeCount}</TD><TD className="num text-right">{l.attempts}</TD>
                  <TD className="text-xs">{l.sentAt ? dateTime(l.sentAt) : l.nextRetryAt ? `retry ${relative(l.nextRetryAt)}` : '—'}</TD>
                  <TD className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => download(`/attendance/monthly-reports/${l.id}/download`, { format: 'xlsx' }).catch((e) => toast.error(errorMessage(e)))}><Download /> Excel</Button>
                      {can('ATTENDANCE_MANAGE') && <Button variant="ghost" size="sm" onClick={() => resend(l)}><Send /> {l.status === 'SENT' ? 'Resend' : 'Send'}</Button>}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
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
          {lk.users.length > 1 && <NativeSelect aria-label="Employee" className="h-8 w-44 text-xs" value={f.userId ?? ''} onChange={(e) => { setPage(0); setF({ ...f, userId: e.target.value || undefined }); }}><option value="">All employees</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect>}
          <NativeSelect aria-label="Event type" className="h-8 w-44 text-xs" value={f.type ?? ''} onChange={(e) => { setPage(0); setF({ ...f, type: e.target.value || undefined }); }}>
            <option value="">All events</option>
            {['LOGIN', 'LOGOUT', 'BREAK_START', 'BREAK_END', 'IDLE_START', 'IDLE_END', 'STATUS_CONFIRMED', 'REMINDER_SENT', 'CORRECTION'].map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}
          </NativeSelect>
          <Input type="date" aria-label="From" className="h-8 w-40 text-xs" value={f.from ?? ''} onChange={(e) => setF({ ...f, from: e.target.value || undefined })} />
          <Input type="date" aria-label="To" className="h-8 w-40 text-xs" value={f.to ?? ''} onChange={(e) => setF({ ...f, to: e.target.value || undefined })} />
        </div>
        {can('ATTENDANCE_MANAGE') && <Button size="sm" variant="outline" onClick={() => setCorr(true)}>Correct a record</Button>}
      </CardHeader>
      <CardContent className="px-0">
        {error && <div className="px-5"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (
          <>
            <Table>
              <THead><TR><TH>When</TH><TH>Employee</TH><TH>Event</TH><TH>Source</TH><TH>Details</TH></TR></THead>
              <TBody>
                {data.content.map((e) => (
                  <TR key={e.id}><TD className="num whitespace-nowrap text-xs">{dateTime(e.occurredAt)}</TD><TD>{e.userName}</TD><TD><Badge tone={e.eventType === 'CORRECTION' ? 'red' : e.eventType.startsWith('IDLE') ? 'violet' : e.eventType.startsWith('BREAK') ? 'amber' : 'blue'}>{titleCase(e.eventType)}</Badge></TD>
                    <TD className="text-xs">{titleCase(e.source)}</TD><TD className="max-w-md truncate text-xs text-muted-foreground" title={e.details}>{e.details}</TD></TR>
                ))}
              </TBody>
            </Table>
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
