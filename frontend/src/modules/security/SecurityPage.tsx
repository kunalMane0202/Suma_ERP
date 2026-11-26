import { useState } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Ban, Download, LogOut, Monitor, Plus, ShieldAlert, ShieldCheck, ShieldX, Shuffle, Smartphone, Trash2 } from 'lucide-react';
import { FeedItem, IconTile, SearchField } from '@/components/layout';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { TabsContent, TabsList, TabsTrigger, UrlTabs } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Switch } from '@/components/ui/switch';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Avatar, Can, EmptyState, ErrorState, PageHeader, Pagination, Stat } from '@/components/common';
import { ChartLegend, RadialGauge } from '@/components/viz';
import { useApi } from '@/hooks/useApi';
import { useAuth } from '@/contexts/AuthContext';
import { api, download, errorMessage } from '@/services/api';
import { CHART, SERIES, tooltipStyle } from '@/utils/chart';
import { dateTime, relative, titleCase } from '@/utils/format';
import type { Page } from '@/types/api';

interface Dashboard {
  since: string; successfulLogins: number; failedLogins: number; unknownUserAttempts: number; unauthenticatedRequests: number; accessDenied: number;
  blockedRequests: number; uniqueIps: number; suspiciousEvents: number; activeSessions: number; activeUsers: number; ipChanges: number;
  repeatedFailures: { ip: string; attempts: number; lastAttempt: string }[]; trend: { bucket: string; success: number; failed: number }[];
  failureCategories: { category: string; count: number }[]; topIps: { ip: string; events: number; failures: number; anonymous: number; last_seen: string }[];
}
interface LoginRow { id: string; userName?: string; usernameAttempted?: string; success: boolean; failureCategory?: string; ipAddress: string; ipVersion: number; device?: string; browser?: string; attemptCount: number; suspicious: boolean; suspiciousReasons?: string; createdAt: string; correlationId?: string }
interface AnonRow { id: string; ipAddress: string; ipVersion: number; httpMethod: string; endpoint: string; category: string; statusCode: number; username?: string; userAgent?: string; createdAt: string; correlationId?: string }
interface IpRow { id: string; ipAddress: string; ipVersion: number; userId?: string; activityType: string; details?: string; createdAt: string }
interface SessionRow { id: string; userId: string; userName?: string; employeeCode?: string; role?: string; status: string; ipAddress?: string; lastIp?: string; ipChanged: boolean; device?: string; browser?: string; createdAt: string; lastSeenAt: string; expiresAt: string; endedReason?: string }
interface Rule { id: string; cidr: string; ruleType: 'ALLOW' | 'DENY'; description?: string; active: boolean; expiresAt?: string; createdAt: string }

const catTone: Record<string, BadgeTone> = { UNKNOWN_USER: 'violet', INVALID_CREDENTIALS: 'amber', ACCOUNT_LOCKED: 'red', ACCOUNT_DISABLED: 'neutral', BLOCKED_IP: 'red', RATE_LIMITED: 'red',
  MISSING_AUTH: 'neutral', INVALID_TOKEN: 'amber', EXPIRED_SESSION: 'neutral', ACCESS_DENIED: 'red', REVOKED_SESSION: 'amber' };

export default function SecurityPage() {
  return (
    <>
      <PageHeader title="Login security & IP activity" description="Successful, failed, unknown-user and unauthenticated access with source IP addresses. Times are stored in UTC and shown in your local time. Approximate IP location is not proof of physical location." />
      <UrlTabs fallback="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger><TabsTrigger value="logins">Sign-ins</TabsTrigger><TabsTrigger value="anonymous">Unauthenticated</TabsTrigger>
          <TabsTrigger value="ip">IP activity</TabsTrigger><TabsTrigger value="sessions">Active sessions</TabsTrigger><TabsTrigger value="rules">IP rules</TabsTrigger>
        </TabsList>
        <TabsContent value="overview"><Overview /></TabsContent>
        <TabsContent value="logins"><Logins /></TabsContent>
        <TabsContent value="anonymous"><Anonymous /></TabsContent>
        <TabsContent value="ip"><IpActivity /></TabsContent>
        <TabsContent value="sessions"><Sessions /></TabsContent>
        <TabsContent value="rules"><Rules /></TabsContent>
      </UrlTabs>
    </>
  );
}

function Overview() {
  const [hours, setHours] = useState(24);
  const { data: d, error, reload } = useApi<Dashboard>('/security/dashboard', { hours }, { interval: 60_000 });
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!d) return <TableSkeleton />;
  return (
    <div className="space-y-4">
      <NativeSelect aria-label="Period" className="w-40" value={hours} onChange={(e) => setHours(Number(e.target.value))}>
        <option value={24}>Last 24 hours</option><option value={168}>Last 7 days</option><option value={720}>Last 30 days</option>
      </NativeSelect>
      <div className="grid gap-4 lg:grid-cols-12">
        <Card tone="ink" className="relative overflow-hidden lg:col-span-5">
          <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-[#8B5CF6]/40 blur-3xl" />
          <CardContent className="relative flex items-center gap-6 pt-6">
            <RadialGauge value={d.successfulLogins + d.failedLogins === 0 ? 100 : (d.successfulLogins * 100) / (d.successfulLogins + d.failedLogins)} size={150} stroke={14} tone="light"
              label={<span className="display-num text-3xl text-white">{d.successfulLogins + d.failedLogins === 0 ? '—' : `${Math.round((d.successfulLogins * 100) / (d.successfulLogins + d.failedLogins))}%`}</span>} sublabel={<span className="text-white/70">success</span>} />
            <div className="space-y-3">
              <p className="font-display text-lg font-semibold">Sign-in health</p>
              <div className="grid grid-cols-2 gap-x-6 gap-y-2">
                <div><p className="text-xs text-white/60">Successful</p><p className="display-num text-2xl text-[#5EEAD4]">{d.successfulLogins}</p></div>
                <div><p className="text-xs text-white/60">Failed</p><p className="display-num text-2xl text-[#FDA4AF]">{d.failedLogins}</p></div>
                <div><p className="text-xs text-white/60">Unknown users</p><p className="display-num text-2xl">{d.unknownUserAttempts}</p></div>
                <div><p className="text-xs text-white/60">Suspicious</p><p className="display-num text-2xl text-[#FCD34D]">{d.suspiciousEvents}</p></div>
              </div>
            </div>
          </CardContent>
        </Card>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:col-span-7">
          <Stat index={0} label="Successful sign-ins" value={d.successfulLogins} tone="ok" />
          <Stat index={1} label="Failed sign-ins" value={d.failedLogins} tone={d.failedLogins ? 'warn' : 'default'} />
          <Stat index={2} label="Unknown usernames" value={d.unknownUserAttempts} tone={d.unknownUserAttempts ? 'idle' : 'default'} />
          <Stat index={3} label="Unauthenticated requests" value={d.unauthenticatedRequests} />
          <Stat index={4} label="Access denied" value={d.accessDenied} tone={d.accessDenied ? 'breach' : 'default'} />
          <Stat index={5} label="Blocked by IP rule" value={d.blockedRequests} />
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:col-span-12">
          <Stat index={6} label="Unique IP addresses" value={d.uniqueIps} tone="primary" />
          <Stat index={7} label="Suspicious events" value={d.suspiciousEvents} tone={d.suspiciousEvents ? 'breach' : 'default'} />
          <Stat index={8} label="Active sessions" value={d.activeSessions} hint={`${d.activeUsers} users`} tone="primary" />
          <Stat index={9} label="IP changes" value={d.ipChanges} hint="Informational, never blocks" />
        </div>
      </div>
      <div className="grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader><div><CardTitle>Sign-in attempts</CardTitle><CardDescription>Successful and failed over time</CardDescription></div><ChartLegend items={[[SERIES.resolved, 'Successful'], [SERIES.breached, 'Failed']]} /></CardHeader>
          <CardContent className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={d.trend.map((t) => ({ ...t, label: new Date(t.bucket).toLocaleString(undefined, hours <= 48 ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' }) }))} margin={{ left: -20, right: 8 }}>
                <CartesianGrid stroke={CHART.grid} strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} minTickGap={20} />
                <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} />
                <RTooltip {...tooltipStyle} />
                <Area type="monotone" dataKey="success" name="Successful" stroke={SERIES.resolved} fill={SERIES.resolved} fillOpacity={0.12} strokeWidth={2} />
                <Area type="monotone" dataKey="failed" name="Failed" stroke={SERIES.breached} fill={SERIES.breached} fillOpacity={0.1} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><div><CardTitle>Repeated failures by IP</CardTitle><CardDescription>3 or more failed attempts in the period</CardDescription></div></CardHeader>
          <CardContent>
            {d.repeatedFailures.length === 0 ? <p className="text-sm text-muted-foreground">No repeated failures.</p> : (
              <ul className="space-y-2">{d.repeatedFailures.map((r) => (
                <li key={r.ip} className="flex items-center justify-between rounded-xl bg-muted/60 px-3 py-2.5 text-sm"><span className="font-mono text-xs">{r.ip}</span><span className="text-right"><b className="num text-breach">{r.attempts}</b> <span className="text-xs text-muted-foreground">· {relative(r.lastAttempt)}</span></span></li>
              ))}</ul>
            )}
            <p className="mt-4 text-sm font-medium">Failure reasons</p>
            <ul className="mt-2 space-y-1">{d.failureCategories.map((c) => <li key={c.category} className="flex justify-between text-sm"><Badge tone={catTone[c.category]}>{titleCase(c.category)}</Badge><span className="num">{c.count}</span></li>)}</ul>
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader><div><CardTitle>Most active IP addresses</CardTitle></div></CardHeader>
        <CardContent className="px-0">
          <Table>
            <THead><TR><TH>IP address</TH><TH className="text-right">Events</TH><TH className="text-right">Failed sign-ins</TH><TH className="text-right">Unauthenticated</TH><TH>Last seen</TH></TR></THead>
            <TBody>{d.topIps.map((r) => <TR key={r.ip}><TD className="font-mono text-xs">{r.ip}</TD><TD className="num text-right">{r.events}</TD><TD className="num text-right">{r.failures}</TD><TD className="num text-right">{r.anonymous}</TD><TD className="text-xs">{relative(r.last_seen)}</TD></TR>)}</TBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

function FilterBar({ f, setF, extra, exportType }: { f: Record<string, string>; setF: (f: Record<string, string>) => void; extra?: React.ReactNode; exportType?: string }) {
  const { can } = useAuth();
  return (
    <div className="flex flex-wrap items-center gap-2 border-b border-border/60 p-4">
      <SearchField label="Filter by IP" placeholder="IP address starts with…" value={f.ip ?? ''} onChange={(v) => setF({ ...f, ip: v })} className="sm:w-56" />
      {extra}
      <Input type="datetime-local" aria-label="From" className="w-52 rounded-full" value={f.fromLocal ?? ''} onChange={(e) => setF({ ...f, fromLocal: e.target.value })} />
      <Input type="datetime-local" aria-label="To" className="w-52 rounded-full" value={f.toLocal ?? ''} onChange={(e) => setF({ ...f, toLocal: e.target.value })} />
      <div className="flex-1" />
      {exportType && can('REPORT_EXPORT') && <Button size="sm" variant="outline" onClick={() => download('/security/reports/export', { type: exportType, ...toParams(f) }).catch((e) => toast.error(errorMessage(e)))}><Download /> Export CSV</Button>}
    </div>
  );
}
function toParams(f: Record<string, string>) {
  const p: Record<string, string> = {};
  if (f.ip) p.ip = f.ip;
  if (f.username) p.username = f.username;
  if (f.category) p.category = f.category;
  if (f.type) p.type = f.type;
  if (f.success) p.success = f.success;
  if (f.fromLocal) p.from = new Date(f.fromLocal).toISOString();
  if (f.toLocal) p.to = new Date(f.toLocal).toISOString();
  return p;
}

function Logins() {
  const [f, setF] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [mode, setMode] = useState<'all' | 'failed' | 'suspicious'>('all');
  const url = mode === 'failed' ? '/security/failed-logins' : mode === 'suspicious' ? '/security/suspicious-activity' : '/security/login-activity';
  const { data, error, loading, reload } = useApi<Page<LoginRow>>(url, { ...toParams(f), page, size: 50 });
  return (
    <Card>
      <FilterBar f={f} setF={(x) => { setPage(0); setF(x); }} exportType={mode === 'failed' ? 'failed' : 'logins'} extra={<>
        <Input className="w-44 rounded-full" placeholder="Username" aria-label="Filter by username" value={f.username ?? ''} onChange={(e) => setF({ ...f, username: e.target.value })} />
        <NativeSelect aria-label="Result" className="w-40 rounded-full" value={mode} onChange={(e) => { setPage(0); setMode(e.target.value as typeof mode); }}>
          <option value="all">All attempts</option><option value="failed">Failed only</option><option value="suspicious">Suspicious only</option>
        </NativeSelect></>} />
      {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No sign-in attempts match" /> : (
        <>
          <Table>
            <THead><TR><TH>When</TH><TH>User / attempted</TH><TH>Result</TH><TH>IP address</TH><TH>Device</TH><TH>Indicators</TH></TR></THead>
            <TBody>{data.content.map((l) => (
              <TR key={l.id}>
                <TD className="num whitespace-nowrap text-xs">{dateTime(l.createdAt)}</TD>
                <TD><div className="flex items-center gap-3"><span className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${l.success ? 'bg-ok/10 text-ok' : l.suspicious ? 'bg-breach/10 text-breach' : 'bg-warn/15 text-warn'}`}>{l.success ? <ShieldCheck className="h-4 w-4" /> : l.suspicious ? <ShieldAlert className="h-4 w-4" /> : <ShieldX className="h-4 w-4" />}</span>
                  <div><p className="font-medium">{l.userName ?? <span className="text-muted-foreground">unknown</span>}</p><p className="text-xs text-muted-foreground">{l.usernameAttempted}</p></div></div></TD>
                <TD>{l.success ? <Badge tone="green">Success</Badge> : <Badge tone={catTone[l.failureCategory ?? ''] ?? 'red'}>{titleCase(l.failureCategory)}</Badge>}{!l.success && l.attemptCount > 1 && <p className="mt-0.5 text-xs text-muted-foreground">attempt {l.attemptCount}</p>}</TD>
                <TD><span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px]">{l.ipAddress}</span><span className="ml-1 text-[11px] text-muted-foreground">v{l.ipVersion}</span></TD>
                <TD className="text-xs">{l.browser} · {l.device}</TD>
                <TD>{l.suspicious && <Badge tone="red">Suspicious</Badge>}{l.suspiciousReasons && <p className="mt-0.5 text-xs text-muted-foreground">{l.suspiciousReasons.split(',').map(titleCase).join(', ')}</p>}</TD>
              </TR>
            ))}</TBody>
          </Table>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </>
      ))}
    </Card>
  );
}

function Anonymous() {
  const [f, setF] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi<Page<AnonRow>>('/security/anonymous-access', { ...toParams(f), page, size: 50 });
  return (
    <Card>
      <FilterBar f={f} setF={(x) => { setPage(0); setF(x); }} exportType="anonymous" extra={
        <NativeSelect aria-label="Category" className="w-48 rounded-full" value={f.category ?? ''} onChange={(e) => setF({ ...f, category: e.target.value })}>
          <option value="">All categories</option>{['MISSING_AUTH', 'INVALID_TOKEN', 'EXPIRED_SESSION', 'REVOKED_SESSION', 'ACCESS_DENIED', 'BLOCKED_IP'].map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}
        </NativeSelect>} />
      {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No unauthenticated requests recorded" /> : (
        <>
          <Table>
            <THead><TR><TH>When</TH><TH>Category</TH><TH>Request</TH><TH>IP address</TH><TH>User agent</TH><TH>Correlation ID</TH></TR></THead>
            <TBody>{data.content.map((l) => (
              <TR key={l.id}>
                <TD className="num whitespace-nowrap text-xs">{dateTime(l.createdAt)}</TD>
                <TD><Badge tone={catTone[l.category] ?? 'neutral'}>{titleCase(l.category)}</Badge>{l.username && <p className="mt-0.5 text-xs text-muted-foreground">{l.username}</p>}</TD>
                <TD className="font-mono text-xs"><span className="mr-1 rounded bg-primary/10 px-1.5 py-0.5 font-semibold text-primary">{l.httpMethod}</span>{l.endpoint} <span className="text-muted-foreground">→ {l.statusCode}</span></TD>
                <TD><span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px]">{l.ipAddress}</span></TD>
                <TD className="max-w-[14rem] truncate text-xs text-muted-foreground" title={l.userAgent}>{l.userAgent}</TD>
                <TD className="font-mono text-[11px] text-muted-foreground">{l.correlationId?.slice(0, 8)}</TD>
              </TR>
            ))}</TBody>
          </Table>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </>
      ))}
    </Card>
  );
}

function IpActivity() {
  const [f, setF] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi<Page<IpRow>>('/security/ip-activity', { ...toParams(f), page, size: 50 });
  return (
    <Card>
      <FilterBar f={f} setF={(x) => { setPage(0); setF(x); }} exportType="ip" extra={
        <NativeSelect aria-label="Activity" className="w-48 rounded-full" value={f.type ?? ''} onChange={(e) => setF({ ...f, type: e.target.value })}>
          <option value="">All activity</option>{['LOGIN_SUCCESS', 'LOGIN_FAILURE', 'UNKNOWN_USER', 'ANONYMOUS_ACCESS', 'ACCESS_DENIED', 'IP_CHANGE', 'BLOCKED', 'SUSPICIOUS'].map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}
        </NativeSelect>} />
      {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <TableSkeleton /> : data && (
        <>
          {data.content.length === 0 ? <EmptyState title="No IP activity matches" /> : (
            <ul className="space-y-0.5 p-2">
              {data.content.map((l) => {
                const t = l.activityType; const bad = t === 'SUSPICIOUS' || t === 'BLOCKED';
                return (
                  <FeedItem key={l.id} icon={t === 'LOGIN_SUCCESS' ? ShieldCheck : t === 'IP_CHANGE' ? Shuffle : bad ? ShieldAlert : ShieldX}
                    tone={t === 'LOGIN_SUCCESS' ? 'bg-ok/10 text-ok' : t === 'IP_CHANGE' ? 'bg-sky/15 text-sky' : bad ? 'bg-breach/10 text-breach' : 'bg-warn/15 text-warn'}
                    title={<span className="flex flex-wrap items-center gap-2"><span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px]">{l.ipAddress}</span><span className="text-[11px] text-muted-foreground">IPv{l.ipVersion}</span>
                      <Badge tone={t === 'LOGIN_SUCCESS' ? 'green' : t === 'IP_CHANGE' ? 'blue' : bad ? 'red' : 'amber'}>{titleCase(t)}</Badge></span>}
                    meta={l.details} right={<span className="display-num whitespace-nowrap text-xs text-muted-foreground">{dateTime(l.createdAt)}</span>} />
                );
              })}
            </ul>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </>
      )}
    </Card>
  );
}

function Sessions() {
  const { user } = useAuth();
  const [status, setStatus] = useState('ACTIVE');
  const { data, error, loading, reload } = useApi<Page<SessionRow>>('/security/active-sessions', { status, size: 100 }, { interval: 60_000 });
  const [target, setTarget] = useState<SessionRow | null>(null);
  const [reason, setReason] = useState('');
  const terminate = async () => {
    try {
      await api.post(`/security/sessions/${target!.id}/terminate`, { reason: reason || undefined });
      toast.success(`Session for ${target!.userName} terminated`);
      setTarget(null); setReason(''); reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card>
      <div className="flex items-center gap-2 border-b border-border/60 p-4">
        <NativeSelect aria-label="Session status" className="w-44 rounded-full" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="ACTIVE">Active</option><option value="TERMINATED">Terminated</option><option value="EXPIRED">Expired</option><option value="LOGGED_OUT">Signed out</option>
        </NativeSelect>
      </div>
      {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No sessions" /> : (
        <Table>
          <THead><TR><TH>User</TH><TH>Signed in</TH><TH>Last seen</TH><TH>IP address</TH><TH>Device</TH><TH /></TR></THead>
          <TBody>{data.content.map((s) => (
            <TR key={s.id}>
              <TD><div className="flex items-center gap-3"><Avatar name={s.userName ?? "?"} className="h-9 w-9 text-[11px]" /><div><p className="font-semibold">{s.userName}{s.id === user?.sessionId && <Badge tone="blue" className="ml-2">This session</Badge>}</p><p className="text-xs text-muted-foreground">{s.employeeCode} · {s.role}</p></div></div></TD>
              <TD className="num text-xs">{dateTime(s.createdAt)}</TD><TD className="text-xs">{relative(s.lastSeenAt)}</TD>
              <TD><span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px]">{s.ipAddress}</span>{s.ipChanged && <span className="mt-1 block font-mono text-[11px] text-warn">now {s.lastIp}</span>}</TD>
              <TD className="text-xs"><span className="inline-flex items-center gap-1.5">{/mobile|phone|android|ios/i.test(s.device ?? '') ? <Smartphone className="h-3.5 w-3.5 text-muted-foreground" /> : <Monitor className="h-3.5 w-3.5 text-muted-foreground" />}{s.browser} · {s.device}</span>{s.endedReason && <span className="block text-muted-foreground">{s.endedReason}</span>}</TD>
              <TD className="text-right">{s.status === 'ACTIVE' && s.id !== user?.sessionId && <Can permission="SECURITY_MANAGE"><Button size="sm" variant="outline" onClick={() => setTarget(s)}><LogOut /> Terminate</Button></Can>}</TD>
            </TR>
          ))}</TBody>
        </Table>
      ))}
      <Dialog open={!!target} onOpenChange={(o) => !o && setTarget(null)} title={`Terminate session for ${target?.userName ?? ''}?`}
        description="The person is signed out immediately, their refresh token is revoked and their attendance session is closed as terminated by an administrator."
        footer={<><Button variant="outline" onClick={() => setTarget(null)}>Cancel</Button><Button variant="destructive" onClick={terminate}>Terminate session</Button></>}>
        <Field label="Reason (recorded in the audit trail)" htmlFor="term-reason"><Input id="term-reason" value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      </Dialog>
    </Card>
  );
}

function Rules() {
  const { data, reload } = useApi<Rule[]>('/security/ip-rules');
  const [form, setForm] = useState<{ cidr: string; ruleType: 'ALLOW' | 'DENY'; description: string } | null>(null);
  const save = async () => {
    try { await api.post('/security/ip-rules', form); toast.success('IP rule added'); setForm(null); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const allowCount = (data ?? []).filter((r) => r.ruleType === 'ALLOW' && r.active).length;
  return (
    <Card>
      <CardHeader>
        <div><CardTitle>IP allow and deny rules</CardTitle><CardDescription>Deny rules block matching addresses. When any allow rule is active, only allow-listed addresses can use SUMA. Changing IP addresses alone never blocks anyone.</CardDescription></div>
        <Can permission="SECURITY_MANAGE"><Button size="sm" onClick={() => setForm({ cidr: '', ruleType: 'DENY', description: '' })}><Plus /> Add rule</Button></Can>
      </CardHeader>
      <CardContent className="px-0">
        {allowCount > 0 && <p className="mx-5 mb-3 rounded-xl border border-warn/40 bg-warn/10 px-3 py-2 text-sm text-warn">Allow-list mode is on: {allowCount} allow rule(s) active.</p>}
        {(data ?? []).length === 0 ? <EmptyState icon={<Ban />} title="No IP rules" description="All addresses can reach the sign-in page." /> : (
          <div className="grid gap-3 px-5 sm:grid-cols-2 xl:grid-cols-3">{data!.map((r) => (
            <div key={r.id} className={`relative overflow-hidden rounded-2xl p-4 ${r.ruleType === 'DENY' ? 'bg-breach/5 ring-1 ring-breach/20' : 'bg-ok/5 ring-1 ring-ok/20'} ${r.active ? '' : 'opacity-60'}`}>
              <div className="flex items-start gap-3">
                <IconTile icon={r.ruleType === 'DENY' ? Ban : ShieldCheck} tone={r.ruleType === 'DENY' ? 'coral' : 'mint'} />
                <div className="min-w-0 flex-1"><p className="truncate font-mono text-sm font-semibold">{r.cidr}</p><Badge tone={r.ruleType === 'DENY' ? 'red' : 'green'}>{r.ruleType === 'DENY' ? 'Deny' : 'Allow'}</Badge></div>
                <Can permission="SECURITY_MANAGE" fallback={<span className="text-xs">{r.active ? 'Active' : 'Inactive'}</span>}><Switch checked={r.active} aria-label="Rule active" onCheckedChange={async (v) => { await api.patch(`/security/ip-rules/${r.id}`, { active: v }); reload(); }} /></Can>
              </div>
              {r.description && <p className="mt-3 text-sm">{r.description}</p>}
              <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground"><span>Added {dateTime(r.createdAt)}</span>
                <Can permission="SECURITY_MANAGE"><Button variant="ghost" size="icon" aria-label="Delete rule" onClick={async () => { if (!window.confirm(`Delete the ${r.ruleType === 'DENY' ? 'deny' : 'allow'} rule for ${r.cidr}?`)) return; await api.delete(`/security/ip-rules/${r.id}`); reload(); }}><Trash2 /></Button></Can></div>
            </div>
          ))}</div>
        )}
      </CardContent>
      <Dialog open={!!form} onOpenChange={(o) => !o && setForm(null)} title="Add IP rule" description="IPv4 or IPv6 address or CIDR range, e.g. 203.0.113.0/24 or 2001:db8::/32."
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Cancel</Button><Button disabled={!form?.cidr} onClick={save}>Add rule</Button></>}>
        {form && <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Address or CIDR" htmlFor="r-cidr"><Input id="r-cidr" value={form.cidr} onChange={(e) => setForm({ ...form, cidr: e.target.value })} /></Field>
          <Field label="Type" htmlFor="r-type"><NativeSelect id="r-type" value={form.ruleType} onChange={(e) => setForm({ ...form, ruleType: e.target.value as 'ALLOW' | 'DENY' })}><option value="DENY">Deny</option><option value="ALLOW">Allow</option></NativeSelect></Field>
          <Field label="Description" htmlFor="r-desc" className="sm:col-span-2"><Input id="r-desc" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
        </div>}
      </Dialog>
    </Card>
  );
}
