import { useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Building2, CheckCircle2, FolderKanban, Inbox, ShieldCheck, Siren, Trophy } from 'lucide-react';
import { AvatarStack, BigNumber, LiveDot, RadialGauge, Sparkline } from '@/components/viz';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input, NativeSelect } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { ErrorState, PageHeader, Stat } from '@/components/common';
import { Skeleton } from '@/components/ui/skeleton';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { CHART, PRIORITY_COLORS, tooltipStyle } from '@/utils/chart';
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

      <Card className="mb-5 p-2.5">
        <div className="flex flex-wrap items-end gap-2">
          <div className="flex rounded-full bg-muted p-1" role="group" aria-label="Date range">
            {RANGES.map((r) => (
              <button key={r.days} onClick={() => { setRange(r.days); setCustom({}); }}
                className={`rounded-full px-3 py-1 text-xs font-semibold ${range === r.days && !custom.from ? 'bg-brand text-white shadow-glow' : 'text-muted-foreground hover:text-foreground'}`}>{r.label}</button>
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
        <div className="grid gap-4 md:grid-cols-12">{[5, 3, 4, 4, 8].map((c, i) => <Skeleton key={i} className={`h-[220px] md:col-span-${c}`} />)}</div>
      ) : k && data && (
        <motion.div className="grid grid-cols-1 gap-4 md:grid-cols-12" initial="hidden" animate="show"
          variants={{ show: { transition: { staggerChildren: 0.06 } } }}>
          {/* Hero: ticket volume */}
          <Bento className="md:col-span-7 xl:col-span-5 xl:row-span-2" tone="brand">
            <div className="relative flex h-full flex-col overflow-hidden p-6">
              <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-white/10 blur-2xl" />
              <div className="pointer-events-none absolute -bottom-24 left-10 h-56 w-56 rounded-full bg-[#F0506E]/25 blur-3xl" />
              <div className="relative flex items-center justify-between">
                <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold"><Inbox className="h-3.5 w-3.5" /> Tickets received</span>
                <span className="text-xs text-white/75">{params.from} → {params.to}</span>
              </div>
              <BigNumber value={k.ticketsReceived} className="relative mt-6 text-[64px] leading-none" />
              <p className="relative mt-2 text-sm text-white/80">{k.ticketsResolved} resolved · average {k.avgResolutionHours}h to resolve</p>
              <div className="relative mt-auto pt-6">
                <Sparkline data={data.trend.map((t) => Number(t.received))} width={520} height={86} color="#ffffff" className="h-[86px] w-full" />
                <div className="mt-4 grid grid-cols-3 gap-2">
                  <HeroChip label="Open now" value={k.pending} to="/tickets" />
                  <HeroChip label="Overdue" value={k.overdue} to="/tickets?overdue=true" />
                  <HeroChip label="Critical open" value={k.criticalOpen} to="/tickets?view=breached" />
                </div>
              </div>
            </div>
          </Bento>

          {/* SLA health */}
          <Bento className="md:col-span-5 xl:col-span-3 xl:row-span-2">
            <div className="flex h-full flex-col items-center p-6 text-center">
              <div className="flex w-full items-center justify-between"><h3 className="font-display text-[16px] font-semibold">SLA compliance</h3><ShieldCheck className="h-4 w-4 text-muted-foreground" /></div>
              <div className="my-auto py-4">
                <RadialGauge value={k.slaCompliancePercent} size={176} stroke={16} tone={k.slaCompliancePercent >= 95 ? 'ok' : k.slaCompliancePercent >= 85 ? 'warn' : 'breach'}
                  label={<span className="display-num text-[34px] leading-none">{k.slaCompliancePercent.toFixed(1)}<span className="text-lg">%</span></span>} sublabel="of resolved tickets" />
              </div>
              <div className="grid w-full grid-cols-2 gap-2 text-left">
                <MiniStat label="SLA breaches" value={k.slaBreaches} tone="text-breach" />
                <MiniStat label="Overdue" value={k.overdue} tone="text-warn" />
              </div>
            </div>
          </Bento>

          {/* Accounts + people */}
          <div className="grid grid-cols-2 gap-4 md:col-span-12 xl:col-span-4 xl:row-span-2">
            <Stat index={1} label="Active clients" value={k.activeClients} icon={<Building2 />} tone="primary" />
            <Stat index={2} label="Projects" value={k.totalProjects} icon={<FolderKanban />} tone="idle" />
            <Stat index={3} label="Resolved" value={k.ticketsResolved} icon={<CheckCircle2 />} tone="ok" hint={`${k.avgResolutionHours}h average`} />
            <Stat index={4} label="Critical open" value={k.criticalOpen} icon={<Siren />} tone={k.criticalOpen ? 'breach' : 'default'} hint={<Link className="font-semibold text-primary hover:underline" to="/tickets">Open queue</Link>} />
          </div>

          {/* Who's on now */}
          {wf && (
            <Bento className="md:col-span-5 xl:col-span-4" tone="ink">
              <div className="flex h-full flex-col p-6">
                <div className="flex items-center justify-between">
                  <span className="inline-flex items-center gap-2 text-sm font-semibold"><LiveDot /> Working now</span>
                  <Link to="/attendance/team" className="text-xs font-semibold text-white/70 hover:text-white">Team attendance</Link>
                </div>
                <div className="mt-4 flex items-end gap-3">
                  <BigNumber value={wf.currentlyActive} className="text-[52px] leading-none" />
                  <span className="pb-2 text-sm text-white/60">of {wf.employeesInScope} people</span>
                </div>
                <AvatarStack className="mt-4" max={7} names={wf.live.filter((r) => ['WORKING', 'ON_BREAK', 'IDLE', 'AWAY'].includes(r.status)).map((r) => r.fullName)} />
                <div className="mt-auto grid grid-cols-3 gap-2 pt-5 text-xs">
                  <InkChip label="On break" value={wf.onBreak} color="bg-warn" />
                  <InkChip label="Idle" value={wf.idle} color="bg-idle" />
                  <InkChip label="Missing logout" value={wf.missingLogout} color="bg-breach" />
                </div>
              </div>
            </Bento>
          )}

          {/* Ticket flow */}
          <Bento className={wf ? 'md:col-span-7 xl:col-span-8' : 'md:col-span-12'}>
            <CardHeader><div><CardTitle>Ticket flow</CardTitle><CardDescription>Received, resolved and breached per day</CardDescription></div>
              <div className="flex gap-3 text-xs text-muted-foreground"><Legend c={CHART.primary} l="Received" /><Legend c={CHART.ok} l="Resolved" /><Legend c={CHART.breach} l="Breached" /></div></CardHeader>
            <CardContent className="h-[250px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data.trend} margin={{ left: -18, right: 8, top: 8 }}>
                  <defs>
                    <linearGradient id="gRec" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={CHART.primary} stopOpacity={0.35} /><stop offset="100%" stopColor={CHART.violet} stopOpacity={0} /></linearGradient>
                    <linearGradient id="gRes" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={CHART.ok} stopOpacity={0.25} /><stop offset="100%" stopColor={CHART.ok} stopOpacity={0} /></linearGradient>
                  </defs>
                  <CartesianGrid stroke={CHART.grid} strokeDasharray="2 6" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 11, fill: CHART.axis }} tickLine={false} axisLine={false} minTickGap={24} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: CHART.axis }} tickLine={false} axisLine={false} />
                  <RTooltip {...tooltipStyle} />
                  <Area type="monotone" dataKey="received" name="Received" stroke={CHART.primary} strokeWidth={2.5} fill="url(#gRec)" animationDuration={700} />
                  <Area type="monotone" dataKey="resolved" name="Resolved" stroke={CHART.ok} strokeWidth={2.5} fill="url(#gRes)" animationDuration={800} />
                  <Area type="monotone" dataKey="breached" name="Breached" stroke={CHART.breach} strokeWidth={1.5} strokeDasharray="4 4" fill="transparent" animationDuration={900} />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Bento>

          {/* Priority mix */}
          <Bento className="md:col-span-6 xl:col-span-4">
            <CardHeader><div><CardTitle>Priority mix</CardTitle><CardDescription>Tickets received in range</CardDescription></div></CardHeader>
            <CardContent className="flex items-center gap-4">
              <div className="relative h-[170px] w-[170px] shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={data.priorityDistribution} dataKey="count" nameKey="label" innerRadius="66%" outerRadius="100%" paddingAngle={3} cornerRadius={6} stroke="none" animationDuration={700}>
                      {data.priorityDistribution.map((p) => <Cell key={p.priority} fill={PRIORITY_COLORS[p.priority] ?? CHART.primary} />)}
                    </Pie>
                    <RTooltip {...tooltipStyle} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 grid place-items-center text-center"><div><p className="display-num text-2xl">{k.ticketsReceived}</p><p className="text-[11px] text-muted-foreground">tickets</p></div></div>
              </div>
              <ul className="flex-1 space-y-2 text-sm">
                {data.priorityDistribution.map((p) => (
                  <li key={p.priority} className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full" style={{ background: PRIORITY_COLORS[p.priority] }} />
                    <span className="flex-1">{p.label}</span><span className="display-num">{p.count}</span></li>
                ))}
              </ul>
            </CardContent>
          </Bento>

          {/* Status */}
          <Bento className="md:col-span-6 xl:col-span-3">
            <CardHeader><div><CardTitle>Where tickets are</CardTitle><CardDescription>Current status of received tickets</CardDescription></div></CardHeader>
            <CardContent className="space-y-2.5">
              {(() => {
                const max = Math.max(1, ...data.statusDistribution.map((x) => Number(x.count)));
                return data.statusDistribution.map((st) => (
                  <div key={st.status}>
                    <div className="mb-1 flex justify-between text-xs"><span className="text-muted-foreground">{st.label}</span><span className="display-num">{st.count}</span></div>
                    <div className="h-2 overflow-hidden rounded-full bg-muted">
                      <motion.div className="h-full rounded-full bg-brand" initial={{ width: 0 }} animate={{ width: `${(Number(st.count) / max) * 100}%` }} transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }} />
                    </div>
                  </div>
                ));
              })()}
            </CardContent>
          </Bento>

          {/* Team leaderboard */}
          <Bento className="md:col-span-12 xl:col-span-5">
            <CardHeader><div><CardTitle>Team performance</CardTitle><CardDescription>Open load, throughput and breaches</CardDescription></div><Trophy className="h-4 w-4 text-warn" /></CardHeader>
            <CardContent className="space-y-2">
              {data.teamPerformance.length === 0 && <p className="text-sm text-muted-foreground">No tickets in this range.</p>}
              {data.teamPerformance.map((t, i) => (
                <div key={t.team} className="flex items-center gap-3 rounded-xl bg-muted/60 px-3 py-2.5">
                  <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg text-xs font-bold ${i === 0 ? 'bg-brand text-white' : 'bg-card text-muted-foreground'}`}>{i + 1}</span>
                  <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{t.team}</p><p className="text-xs text-muted-foreground">{t.open} open · {t.avg_resolution_hours}h avg</p></div>
                  <div className="text-right"><p className="display-num text-lg leading-none text-ok">{t.resolved}</p><p className="text-[11px] text-muted-foreground">resolved</p></div>
                  <div className="w-12 text-right"><p className={`display-num text-lg leading-none ${t.breached ? 'text-breach' : ''}`}>{t.breached}</p><p className="text-[11px] text-muted-foreground">breached</p></div>
                </div>
              ))}
            </CardContent>
          </Bento>

          {/* Workforce strip */}
          {wf && (
            <Bento className="md:col-span-12">
              <div className="flex flex-wrap items-center gap-x-8 gap-y-4 px-6 py-5">
                <div><p className="font-display text-[16px] font-semibold">Workforce today</p><p className="text-xs text-muted-foreground">{wf.date} · {wf.employeesInScope} people in scope</p></div>
                {([['Logged in', wf.loggedInToday, ''], ['Logged out', wf.loggedOut, ''], ['Not logged in', wf.notLoggedIn, ''], ['Breaks', wf.totalBreaksToday, ''],
                  ['Average break', duration(wf.averageBreakSeconds), ''], ['Over break limit', wf.exceedingBreakLimits, wf.exceedingBreakLimits ? 'text-breach' : ''],
                  ['Exceptions', wf.attendanceExceptions, wf.attendanceExceptions ? 'text-warn' : ''], ['Compliance', `${wf.compliancePercent}%`, 'text-ok']] as [string, number | string, string][]).map(([l, v, c]) => (
                  <div key={l}><p className="text-xs text-muted-foreground">{l}</p><p className={`display-num text-xl ${c}`}>{v}</p></div>
                ))}
              </div>
            </Bento>
          )}
        </motion.div>
      )}
    </>
  );
}

const tileVariants = { hidden: { opacity: 0, y: 14, scale: 0.985 }, show: { opacity: 1, y: 0, scale: 1, transition: { type: 'spring' as const, stiffness: 240, damping: 24 } } };

function Bento({ className, tone, children }: { className?: string; tone?: 'brand' | 'ink' | 'plain'; children: React.ReactNode }) {
  return (
    <motion.div variants={tileVariants} whileHover={{ y: -3 }} className={className}>
      <Card tone={tone ?? 'plain'} className="h-full">{children}</Card>
    </motion.div>
  );
}

function HeroChip({ label, value, to }: { label: string; value: number; to: string }) {
  return (
    <Link to={to} className="rounded-xl bg-white/15 px-3 py-2.5 backdrop-blur transition-colors hover:bg-white/20">
      <p className="text-[11px] text-white/75">{label}</p><p className="display-num text-xl leading-tight">{value}</p>
    </Link>
  );
}

function MiniStat({ label, value, tone }: { label: string; value: number; tone: string }) {
  return <div className="rounded-xl bg-muted/70 px-3 py-2"><p className="text-[11px] text-muted-foreground">{label}</p><p className={`display-num text-xl ${value ? tone : ''}`}>{value}</p></div>;
}

function InkChip({ label, value, color }: { label: string; value: number; color: string }) {
  return <div className="rounded-xl bg-white/10 px-3 py-2"><p className="flex items-center gap-1.5 text-white/60"><span className={`h-1.5 w-1.5 rounded-full ${color}`} />{label}</p><p className="display-num text-lg">{value}</p></div>;
}

function Legend({ c, l }: { c: string; l: string }) {
  return <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full" style={{ background: c }} />{l}</span>;
}
