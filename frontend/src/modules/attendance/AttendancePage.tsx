import { useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ShiftRing } from '@/components/viz';
import { Coffee, Eye, LogIn, MonitorSmartphone, Timer } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState, PageHeader } from '@/components/common';
import { ShiftTimeline, TimelineLegend } from '@/components/ShiftTimeline';
import { useAttendance } from '@/contexts/AttendanceContext';
import { useAuth } from '@/contexts/AuthContext';
import { useApi } from '@/hooks/useApi';
import { useNow } from '@/hooks/useNow';
import { BreakControl } from './BreakControl';
import { clock, date, duration, time, titleCase } from '@/utils/format';
import type { AttendanceState, MonthlyReport } from '@/types/attendance';
import { cn } from '@/utils/cn';

const stateStyle: Record<AttendanceState, [string, string]> = {
  WORKING: ['Working', 'bg-ok'], ON_BREAK: ['On break', 'bg-warn'], IDLE: ['No recent activity', 'bg-idle'], AWAY: ['Away', 'bg-muted-foreground'], OFFLINE: ['Signed out', 'bg-muted-foreground'],
};

export default function AttendancePage() {
  const { user } = useAuth();
  const { today } = useAttendance();
  const now = useNow(1000);
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [y, m] = month.split('-').map(Number);
  const history = useApi<MonthlyReport>('/attendance/monthly', { year: y, month: m, userId: user?.id });

  const sessionSeconds = useMemo(() => {
    if (!today) return 0;
    const ongoing = today.currentSession?.status === 'ACTIVE' ? (now - new Date(today.currentSession.loginAt).getTime()) / 1000 - today.currentSession.durationSeconds : 0;
    return today.sessionSeconds + Math.max(0, ongoing);
  }, [today, now]);

  if (!today) return (<><PageHeader title="My attendance" /><div className="grid gap-4 md:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-32" />)}</div></>);
  const [label, dot] = stateStyle[today.currentState];
  const days = history.data?.employees[0]?.days.slice().reverse() ?? [];

  return (
    <>
      <PageHeader title="My attendance" description={`Your record for ${date(today.workDate)} (${today.timezone}). Everything shown here is what your manager can see.`} />
      <div className="grid gap-4 lg:grid-cols-12">
        <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} className="lg:col-span-5">
          <Card className="relative h-full overflow-hidden">
            <div className="pointer-events-none absolute -right-20 -top-20 h-56 w-56 rounded-full bg-violet/15 blur-3xl" />
            <CardContent className="relative flex flex-col items-center pt-6 text-center">
              <div className="flex w-full items-center justify-between">
                <span className="inline-flex items-center gap-2 rounded-full bg-muted px-3 py-1 text-xs font-semibold"><span className={cn('h-2 w-2 rounded-full', dot)} />{label}</span>
                <span className="text-xs text-muted-foreground">{today.timezone}</span>
              </div>
              <div className="my-5">
                <ShiftRing size={232} workedSeconds={sessionSeconds} targetSeconds={8 * 3600} breakSeconds={today.breakSeconds}
                  breakLimitSeconds={today.maxDailyBreakMinutes ? today.maxDailyBreakMinutes * 60 : undefined} idleSeconds={today.idleSeconds}
                  center={<div><p className="text-xs text-muted-foreground">Signed in</p><p className="display-num text-[34px] leading-tight" aria-live="off">{clock(sessionSeconds)}</p><p className="text-xs text-muted-foreground">since {time(today.firstLoginAt, today.timezone)}</p></div>} />
              </div>
              <div className="mb-5 flex flex-wrap justify-center gap-4 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-brand" />Signed in (8h ring)</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-warn" />Breaks</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-idle" />No activity</span>
              </div>
              <div className="w-full rounded-2xl bg-muted/70 p-4 text-left"><BreakControl /></div>
              <p className="mt-3 text-xs text-muted-foreground">{today.maxBreakMinutes ? `Limit ${today.maxBreakMinutes} min per break` : 'No break limit configured'}{today.maxDailyBreakMinutes ? ` · ${today.maxDailyBreakMinutes} min per day` : ''}</p>
            </CardContent>
          </Card>
        </motion.div>

        <div className="grid gap-4 lg:col-span-7">
          <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
            <Metric index={0} icon={<LogIn />} label="First sign-in" value={time(today.firstLoginAt, today.timezone)} tone="primary" />
            <Metric index={1} icon={<Timer />} label="Signed-in time" value={duration(sessionSeconds)} tone="primary" />
            <Metric index={2} icon={<Coffee />} label={`Breaks (${today.breakCount}${today.maxBreaksPerDay ? ` of ${today.maxBreaksPerDay}` : ''})`} value={duration(today.breakSeconds)}
              tone={today.maxDailyBreakMinutes && today.breakSeconds > today.maxDailyBreakMinutes * 60 ? 'breach' : 'warn'} />
            <Metric index={3} icon={<Eye />} label={`No activity (${today.idleCount})`} value={duration(today.idleSeconds)} tone="idle" />
          </div>
          <Card>
            <CardHeader>
              <div><CardTitle>Today</CardTitle><CardDescription>Your day as one strip — hover a segment for details</CardDescription></div>
              {today.exceptions.length > 0 && <div className="flex flex-wrap justify-end gap-1">{today.exceptions.map((e) => <Badge key={e} tone="amber">{titleCase(e)}</Badge>)}</div>}
            </CardHeader>
            <CardContent>
              <ShiftTimeline data={{
                date: today.workDate, timezone: today.timezone,
                sessions: today.sessions.map((s) => ({ loginAt: s.loginAt, logoutAt: s.logoutAt, status: s.status })),
                breaks: today.breaks.map((b) => ({ startedAt: b.startedAt, endedAt: b.endedAt, durationSeconds: b.durationSeconds, exceeded: b.exceeded, category: b.category })),
                idles: today.idles.map((i) => ({ startedAt: i.idleStartedAt, endedAt: i.endedAt, durationSeconds: i.durationSeconds, response: i.response })),
              }} />
              <div className="mt-3"><TimelineLegend /></div>
            </CardContent>
          </Card>
          <Card className="flex-1">
            <CardHeader><div><CardTitle>Today's log</CardTitle><CardDescription>Sessions, breaks and inactivity prompts</CardDescription></div></CardHeader>
            <CardContent tabIndex={0} role="region" aria-label="Today's log" className="max-h-[380px] overflow-y-auto px-0 pb-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
              {today.breaks.length === 0 && today.sessions.length === 0 ? <EmptyState title="Nothing recorded yet today" /> : (
                <Table>
                  <THead><TR><TH>Type</TH><TH>Start</TH><TH>End</TH><TH>Duration</TH><TH>Detail</TH></TR></THead>
                  <TBody>
                    {today.sessions.map((s) => (
                      <TR key={s.id}><TD><Badge tone="blue">Session</Badge></TD><TD className="num">{time(s.loginAt, today.timezone)}</TD><TD className="num">{s.logoutAt ? time(s.logoutAt, today.timezone) : 'active'}</TD>
                        <TD className="num">{duration(s.durationSeconds)}</TD><TD className="text-xs text-muted-foreground">{s.browser} · {s.loginIp}{s.logoutSource ? ` · ended by ${titleCase(s.logoutSource)}` : ''}</TD></TR>
                    ))}
                    {today.breaks.map((b) => (
                      <TR key={b.id}><TD><Badge tone={b.exceeded ? 'red' : 'amber'}>{today.breakCategories.find((c) => c.code === b.category)?.label ?? titleCase(b.category)}</Badge></TD>
                        <TD className="num">{time(b.startedAt, today.timezone)}</TD><TD className="num">{b.endedAt ? time(b.endedAt, today.timezone) : 'in progress'}</TD>
                        <TD className="num">{duration(b.durationSeconds)}</TD><TD className="text-xs text-muted-foreground">{b.exceeded ? 'Over the configured limit' : titleCase(b.startSource)}</TD></TR>
                    ))}
                    {today.idles.map((i) => (
                      <TR key={i.id}><TD><Badge tone="violet">No activity</Badge></TD><TD className="num">{time(i.idleStartedAt, today.timezone)}</TD><TD className="num">{i.endedAt ? time(i.endedAt, today.timezone) : 'open'}</TD>
                        <TD className="num">{duration(i.durationSeconds ?? 0)}</TD><TD className="text-xs text-muted-foreground">{i.response ? `You answered: ${titleCase(i.response)}` : 'Not confirmed'}</TD></TR>
                    ))}
                  </TBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <Card tone="tint" className="mt-4">
        <CardContent className="grid gap-4 pt-6 md:grid-cols-[auto_1fr]">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand text-white shadow-glow"><MonitorSmartphone className="h-5 w-5" /></span>
          <div>
            <h2 className="font-display text-[16px] font-semibold">What SUMA records</h2>
            <div className="mt-2 grid gap-x-8 gap-y-1.5 text-sm text-muted-foreground md:grid-cols-2">
              <p>Sign-in and sign-out times, the network address and browser used for each session.</p>
              <p>Breaks you start and end, and their durations.</p>
              <p>Periods with no keyboard, mouse or touch activity inside this SUMA tab for {today.idleThresholdMinutes} minutes, and the answer you give to the prompt.</p>
              <p>If a session ends without a sign-out (for example the browser was closed), it is closed at the last moment the app was reachable and marked as a missing sign-out.</p>
              <p className="font-medium text-foreground md:col-span-2">SUMA does not capture screens, keystroke content or activity in other applications. Inactivity is never counted as a break unless you say so.</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <div><CardTitle>History</CardTitle><CardDescription>Your days in the selected month</CardDescription></div>
          <Input type="month" aria-label="Month" className="h-8 w-40" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />
        </CardHeader>
        <CardContent>
          {history.error && <ErrorState message={history.error} onRetry={history.reload} />}
          {history.loading && !history.data ? <Skeleton className="h-40" /> : days.length === 0 ? <EmptyState title="No days to show for this month" /> : (
            <ul className="space-y-2">
              {days.map((d) => (
                <li key={d.date} className="grid items-center gap-3 rounded-xl bg-muted/50 px-4 py-3 transition-colors hover:bg-accent sm:grid-cols-[9rem_1fr_16rem]">
                  <div className="text-sm"><p className="font-medium">{date(d.date)}</p><p className="text-xs text-muted-foreground">{d.weeklyOff ? 'Weekly off' : d.present ? `${duration(d.sessionSeconds)} signed in` : 'No sign-in'}</p></div>
                  {d.present ? <ShiftTimeline compact data={{ date: d.date, timezone: history.data!.timezone, sessions: d.sessions, breaks: d.breaks, idles: d.idles }} /> : <div className="h-3 rounded-md bg-muted/50" />}
                  <div className="flex flex-wrap justify-end gap-1 text-xs">
                    {d.breakCount > 0 && <Badge>{d.breakCount} breaks · {duration(d.breakSeconds)}</Badge>}
                    {d.exceptions.filter((e) => e !== 'NO_LOGIN_RECORD' || !d.weeklyOff).map((e) => <Badge key={e} tone={e === 'MISSING_LOGOUT' || e.includes('EXCEEDED') ? 'red' : 'amber'}>{titleCase(e)}</Badge>)}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </>
  );
}

const metricTone: Record<string, string> = { primary: 'bg-primary/10 text-primary', warn: 'bg-warn/15 text-warn', breach: 'bg-breach/10 text-breach', idle: 'bg-idle/15 text-idle' };

function Metric({ icon, label, value, tone, index }: { icon: React.ReactNode; label: string; value: string; tone: string; index: number }) {
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 + index * 0.05, type: 'spring', stiffness: 260, damping: 24 }} whileHover={{ y: -3 }}>
      <Card className="h-full p-4">
        <span className={cn('grid h-9 w-9 place-items-center rounded-xl [&_svg]:size-4', metricTone[tone])}>{icon}</span>
        <p className="mt-3 text-xs text-muted-foreground">{label}</p>
        <p className={cn('display-num mt-0.5 text-[22px]', tone === 'breach' && 'text-breach')}>{value}</p>
      </Card>
    </motion.div>
  );
}
