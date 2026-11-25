import { useMemo, useState } from 'react';
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
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <div>
              <CardTitle>Today</CardTitle>
              <CardDescription className="flex items-center gap-1.5"><span className={cn('h-2 w-2 rounded-full', dot)} />{label}</CardDescription>
            </div>
            {today.exceptions.length > 0 && <div className="flex flex-wrap justify-end gap-1">{today.exceptions.map((e) => <Badge key={e} tone="amber">{titleCase(e)}</Badge>)}</div>}
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <Metric icon={<LogIn />} label="First sign-in" value={time(today.firstLoginAt, today.timezone)} />
              <Metric icon={<Timer />} label="Signed-in time" value={clock(sessionSeconds)} mono />
              <Metric icon={<Coffee />} label={`Breaks (${today.breakCount}${today.maxBreaksPerDay ? ` of ${today.maxBreaksPerDay}` : ''})`} value={duration(today.breakSeconds)}
                warn={!!today.maxDailyBreakMinutes && today.breakSeconds > today.maxDailyBreakMinutes * 60} />
              <Metric icon={<Eye />} label={`No activity (${today.idleCount})`} value={duration(today.idleSeconds)} />
            </div>
            <div className="mt-5">
              <ShiftTimeline data={{
                date: today.workDate, timezone: today.timezone,
                sessions: today.sessions.map((s) => ({ loginAt: s.loginAt, logoutAt: s.logoutAt, status: s.status })),
                breaks: today.breaks.map((b) => ({ startedAt: b.startedAt, endedAt: b.endedAt, durationSeconds: b.durationSeconds, exceeded: b.exceeded, category: b.category })),
                idles: today.idles.map((i) => ({ startedAt: i.idleStartedAt, endedAt: i.endedAt, durationSeconds: i.durationSeconds, response: i.response })),
              }} />
              <div className="mt-2"><TimelineLegend /></div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><div><CardTitle>Breaks</CardTitle><CardDescription>{today.maxBreakMinutes ? `Configured limit: ${today.maxBreakMinutes} min per break` : 'No break limit configured'}{today.maxDailyBreakMinutes ? ` · ${today.maxDailyBreakMinutes} min per day` : ''}</CardDescription></div></CardHeader>
          <CardContent><BreakControl /></CardContent>
        </Card>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader><div><CardTitle>Today's breaks and sessions</CardTitle></div></CardHeader>
          <CardContent className="px-0">
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
        <Card>
          <CardHeader><div><CardTitle>What SUMA records</CardTitle><CardDescription>Attendance monitoring is transparent</CardDescription></div><MonitorSmartphone className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent className="space-y-2.5 text-sm text-muted-foreground">
            <p>Sign-in and sign-out times, the network address and browser used for each session.</p>
            <p>Breaks you start and end, and their durations.</p>
            <p>Periods with no keyboard, mouse or touch activity inside this SUMA tab for {today.idleThresholdMinutes} minutes, and the answer you give to the prompt.</p>
            <p className="text-foreground">SUMA does not capture screens, keystroke content or activity in other applications. Inactivity is never counted as a break unless you say so.</p>
            <p>If a session ends without a sign-out (for example the browser was closed), it is closed at the last moment the app was reachable and marked as a missing sign-out.</p>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader>
          <div><CardTitle>History</CardTitle><CardDescription>Your days in the selected month</CardDescription></div>
          <Input type="month" aria-label="Month" className="h-8 w-40" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />
        </CardHeader>
        <CardContent>
          {history.error && <ErrorState message={history.error} onRetry={history.reload} />}
          {history.loading && !history.data ? <Skeleton className="h-40" /> : days.length === 0 ? <EmptyState title="No days to show for this month" /> : (
            <ul className="divide-y">
              {days.map((d) => (
                <li key={d.date} className="grid items-center gap-2 py-2.5 sm:grid-cols-[8.5rem_1fr_15rem]">
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

function Metric({ icon, label, value, mono, warn }: { icon: React.ReactNode; label: string; value: string; mono?: boolean; warn?: boolean }) {
  return (
    <div>
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground [&_svg]:size-3.5">{icon}{label}</p>
      <p className={cn('mt-1 text-xl font-semibold tracking-tight', mono && 'num', warn && 'text-breach')}>{value}</p>
    </div>
  );
}
