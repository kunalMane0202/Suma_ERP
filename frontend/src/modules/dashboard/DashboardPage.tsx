import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { AlarmClock, Building2, CheckCircle2, Clock3, FolderKanban, Inbox, ShieldAlert, Siren } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input, NativeSelect } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { ErrorState, PageHeader, Stat } from '@/components/common';
import { Skeleton } from '@/components/ui/skeleton';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { CHART, tooltipStyle } from '@/utils/chart';
import { duration, isoDate } from '@/utils/format';
import type { AttendanceDashboard } from '@/types/attendance';

interface Summary {
  kpis: { activeClients: number; totalProjects: number; ticketsReceived: number; ticketsResolved: number; pending: number; overdue: number; slaBreaches: number; slaCompliancePercent: number; avgResolutionHours: number; criticalOpen: number };
  trend: { date: string; received: number; resolved: number; breached: number }[];
  priorityDistribution: { priority: string; label: string; color: string; count: number }[];
  statusDistribution: { status: string; label: string; color: string; count: number }[];
  teamPerformance: { team: string; open: number; resolved: number; breached: number; avg_resolution_hours: number }[];
  topAssignees: { name: string; open: number; resolved: number }[];
  workforce?: AttendanceDashboard;
}

const RANGES = [{ label: 'Today', days: 0 }, { label: '7 days', days: 6 }, { label: '30 days', days: 29 }, { label: '90 days', days: 89 }];

export default function DashboardPage() {
  const { user } = useAuth();
  const lk = useLookups();
  const [range, setRange] = useState(29);
  const [custom, setCustom] = useState<{ from?: string; to?: string }>({});
  const [f, setF] = useState<{ clientId?: string; projectId?: string; teamId?: string; assigneeId?: string; status?: string }>({});
  const params = useMemo(() => {
    const to = custom.to ?? isoDate(new Date());
    const from = custom.from ?? isoDate(new Date(Date.now() - range * 86400000));
    return { from, to, ...Object.fromEntries(Object.entries(f).filter(([, v]) => v)) };
  }, [range, custom, f]);
  const { data, error, loading, reload } = useApi<Summary>('/dashboard/summary', params, { interval: 120_000 });
  const k = data?.kpis;
  const wf = data?.workforce;
  const set = (key: keyof typeof f) => (e: React.ChangeEvent<HTMLSelectElement>) => setF((x) => ({ ...x, [key]: e.target.value || undefined }));

  return (
    <>
      <PageHeader title={`Good ${new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 17 ? 'afternoon' : 'evening'}, ${user?.fullName.split(' ')[0]}`}
        description="Ticket flow, SLA health and who is working right now, for the clients and projects you can access." />

      <Card className="mb-5 p-3">
        <div className="flex flex-wrap items-end gap-2">
          <div className="flex rounded-md border bg-card p-0.5" role="group" aria-label="Date range">
            {RANGES.map((r) => (
              <button key={r.days} onClick={() => { setRange(r.days); setCustom({}); }}
                className={`rounded px-2.5 py-1 text-xs font-medium ${range === r.days && !custom.from ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}>{r.label}</button>
            ))}
          </div>
          <Input type="date" aria-label="From date" className="h-8 w-[9.5rem] text-xs" value={custom.from ?? params.from} onChange={(e) => setCustom((c) => ({ ...c, from: e.target.value }))} />
          <Input type="date" aria-label="To date" className="h-8 w-[9.5rem] text-xs" value={custom.to ?? params.to} onChange={(e) => setCustom((c) => ({ ...c, to: e.target.value }))} />
          {lk.clients.length > 0 && <NativeSelect aria-label="Client" className="h-8 w-40 text-xs" value={f.clientId ?? ''} onChange={set('clientId')}><option value="">All clients</option>{lk.clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect>}
          {lk.projects.length > 0 && <NativeSelect aria-label="Project" className="h-8 w-44 text-xs" value={f.projectId ?? ''} onChange={set('projectId')}><option value="">All projects</option>{lk.projects.filter((p) => !f.clientId || p.clientId === f.clientId).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect>}
          {lk.teams.length > 0 && <NativeSelect aria-label="Team" className="h-8 w-40 text-xs" value={f.teamId ?? ''} onChange={set('teamId')}><option value="">All teams</option>{lk.teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</NativeSelect>}
          {lk.users.length > 1 && <NativeSelect aria-label="Employee" className="h-8 w-40 text-xs" value={f.assigneeId ?? ''} onChange={set('assigneeId')}><option value="">All employees</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect>}
          <NativeSelect aria-label="Status" className="h-8 w-36 text-xs" value={f.status ?? ''} onChange={set('status')}>
            <option value="">All statuses</option>{['NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'REOPENED'].map((s) => <option key={s} value={s}>{s.replace('_', ' ').toLowerCase()}</option>)}
          </NativeSelect>
          {(Object.keys(f).length > 0 || custom.from) && <Button variant="ghost" size="sm" onClick={() => { setF({}); setCustom({}); }}>Clear filters</Button>}
        </div>
      </Card>

      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-5">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="h-[92px]" />)}</div>
      ) : k && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-5">
          <Stat index={0} label="Active clients" value={k.activeClients} icon={<Building2 />} />
          <Stat index={1} label="Projects" value={k.totalProjects} icon={<FolderKanban />} />
          <Stat index={2} label="Tickets received" value={k.ticketsReceived} icon={<Inbox />} tone="primary" />
          <Stat index={3} label="Tickets resolved" value={k.ticketsResolved} icon={<CheckCircle2 />} tone="ok" hint={`Avg ${k.avgResolutionHours}h to resolve`} />
          <Stat index={4} label="Open now" value={k.pending} icon={<Clock3 />} hint={<Link className="text-primary hover:underline" to="/tickets?status=open">View queue</Link>} />
          <Stat index={5} label="Overdue" value={k.overdue} icon={<AlarmClock />} tone={k.overdue ? 'warn' : 'default'} hint={<Link className="text-primary hover:underline" to="/tickets?overdue=true">See overdue</Link>} />
          <Stat index={6} label="SLA breaches" value={k.slaBreaches} icon={<ShieldAlert />} tone={k.slaBreaches ? 'breach' : 'default'} />
          <Stat index={7} label="SLA compliance" value={k.slaCompliancePercent} format={(n) => `${n.toFixed(1)}%`} tone={k.slaCompliancePercent >= 95 ? 'ok' : k.slaCompliancePercent >= 85 ? 'warn' : 'breach'} />
          <Stat index={8} label="Critical open" value={k.criticalOpen} icon={<Siren />} tone={k.criticalOpen ? 'breach' : 'default'} />
          {wf && <Stat index={9} label="Working now" value={wf.currentlyActive} hint={`${wf.onBreak} on break · ${wf.idle} idle`} tone="primary" />}
        </div>
      )}

      {data && (
        <div className="mt-5 grid gap-4 xl:grid-cols-3">
          <Card className="xl:col-span-2">
            <CardHeader><div><CardTitle>Ticket flow</CardTitle><CardDescription>Received, resolved and breached per day</CardDescription></div></CardHeader>
            <CardContent className="h-[280px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data.trend} margin={{ left: -18, right: 8, top: 8 }}>
                  <defs>
                    <linearGradient id="gRec" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={CHART.primary} stopOpacity={0.28} /><stop offset="100%" stopColor={CHART.primary} stopOpacity={0} /></linearGradient>
                    <linearGradient id="gRes" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={CHART.ok} stopOpacity={0.22} /><stop offset="100%" stopColor={CHART.ok} stopOpacity={0} /></linearGradient>
                  </defs>
                  <CartesianGrid stroke={CHART.grid} strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 11, fill: CHART.axis }} tickLine={false} axisLine={false} minTickGap={24} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: CHART.axis }} tickLine={false} axisLine={false} />
                  <RTooltip {...tooltipStyle} />
                  <Area type="monotone" dataKey="received" name="Received" stroke={CHART.primary} strokeWidth={2} fill="url(#gRec)" animationDuration={700} />
                  <Area type="monotone" dataKey="resolved" name="Resolved" stroke={CHART.ok} strokeWidth={2} fill="url(#gRes)" animationDuration={800} />
                  <Area type="monotone" dataKey="breached" name="Breached" stroke={CHART.breach} strokeWidth={1.5} strokeDasharray="4 3" fill="transparent" animationDuration={900} />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><div><CardTitle>Priority mix</CardTitle><CardDescription>Tickets received in range</CardDescription></div></CardHeader>
            <CardContent className="flex h-[280px] flex-col">
              <div className="flex-1">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={data.priorityDistribution} dataKey="count" nameKey="label" innerRadius="58%" outerRadius="88%" paddingAngle={2} stroke="none" animationDuration={700}>
                      {data.priorityDistribution.map((p) => <Cell key={p.priority} fill={{ CRITICAL: CHART.breach, HIGH: '#E0782F', MEDIUM: CHART.warn, LOW: CHART.ok }[p.priority] ?? CHART.primary} />)}
                    </Pie>
                    <RTooltip {...tooltipStyle} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="grid grid-cols-2 gap-1.5 text-xs">
                {data.priorityDistribution.map((p) => (
                  <li key={p.priority} className="flex items-center justify-between rounded bg-muted/60 px-2 py-1"><span>{p.label}</span><span className="num font-semibold">{p.count}</span></li>
                ))}
              </ul>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><div><CardTitle>Status</CardTitle><CardDescription>Where received tickets are now</CardDescription></div></CardHeader>
            <CardContent className="h-[250px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.statusDistribution} layout="vertical" margin={{ left: 8, right: 16 }}>
                  <XAxis type="number" hide allowDecimals={false} />
                  <YAxis type="category" dataKey="label" width={84} tick={{ fontSize: 11, fill: CHART.axis }} tickLine={false} axisLine={false} />
                  <RTooltip {...tooltipStyle} />
                  <Bar dataKey="count" name="Tickets" radius={[0, 4, 4, 0]} fill={CHART.primary} animationDuration={700} barSize={14} />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
          <Card className="xl:col-span-2">
            <CardHeader><div><CardTitle>Team performance</CardTitle><CardDescription>Open load, throughput and SLA breaches by team</CardDescription></div></CardHeader>
            <CardContent className="px-0 pb-2">
              <Table>
                <THead><TR><TH>Team</TH><TH className="text-right">Open</TH><TH className="text-right">Resolved</TH><TH className="text-right">Breached</TH><TH className="text-right">Avg resolution</TH></TR></THead>
                <TBody>
                  {data.teamPerformance.map((t) => (
                    <TR key={t.team}>
                      <TD className="font-medium">{t.team}</TD><TD className="num text-right">{t.open}</TD><TD className="num text-right">{t.resolved}</TD>
                      <TD className={`num text-right ${t.breached ? 'text-breach' : ''}`}>{t.breached}</TD><TD className="num text-right">{t.avg_resolution_hours}h</TD>
                    </TR>
                  ))}
                  {data.teamPerformance.length === 0 && <TR><TD colSpan={5} className="text-center text-muted-foreground">No tickets in this range.</TD></TR>}
                </TBody>
              </Table>
            </CardContent>
          </Card>
        </div>
      )}

      {wf && (
        <Card className="mt-5">
          <CardHeader>
            <div><CardTitle>Workforce today</CardTitle><CardDescription>Attendance for {wf.employeesInScope} employees in your scope · {wf.date}</CardDescription></div>
            <Button asChild variant="outline" size="sm"><Link to="/attendance/team">Open attendance</Link></Button>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {[
                ['Logged in today', wf.loggedInToday, ''], ['Active', wf.currentlyActive, 'text-primary'], ['On break', wf.onBreak, 'text-warn'],
                ['Idle', wf.idle, 'text-idle'], ['Logged out', wf.loggedOut, ''], ['Missing logout', wf.missingLogout, wf.missingLogout ? 'text-breach' : ''],
                ['Breaks today', wf.totalBreaksToday, ''], ['Average break', -1, ''], ['Over break limit', wf.exceedingBreakLimits, wf.exceedingBreakLimits ? 'text-breach' : ''],
                ['Exceptions', wf.attendanceExceptions, wf.attendanceExceptions ? 'text-warn' : ''], ['Not logged in', wf.notLoggedIn, ''], ['Compliance', -2, ''],
              ].map(([label, value, cls]) => (
                <div key={label as string} className="rounded-md border bg-card/60 px-3 py-2.5">
                  <p className="text-xs text-muted-foreground">{label}</p>
                  <p className={`num mt-0.5 text-lg font-semibold ${cls}`}>{value === -1 ? duration(wf.averageBreakSeconds) : value === -2 ? `${wf.compliancePercent}%` : (value as number)}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </>
  );
}
