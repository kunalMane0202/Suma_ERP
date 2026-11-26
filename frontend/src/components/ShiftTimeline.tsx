import { motion, useReducedMotion } from 'motion/react';
import { Tooltip } from '@/components/ui/tooltip';
import { duration, time } from '@/utils/format';
import { cn } from '@/utils/cn';

export interface TimelineInput {
  date: string;                       // yyyy-MM-dd, in tz
  timezone: string;
  sessions: { loginAt: string; logoutAt?: string; ongoing?: boolean; status?: string }[];
  breaks: { startedAt: string; endedAt?: string; durationSeconds: number; exceeded?: boolean; category?: string }[];
  idles: { startedAt: string; endedAt?: string; durationSeconds?: number; response?: string }[];
}

function minuteOfDay(iso: string, tz: string): number {
  const parts = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: tz }).formatToParts(new Date(iso));
  const h = Number(parts.find((p) => p.type === 'hour')?.value ?? 0);
  const m = Number(parts.find((p) => p.type === 'minute')?.value ?? 0);
  return h * 60 + m;
}

function dateIn(iso: string, tz: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(iso));
}

/**
 * The day drawn as one strip: worked time, breaks and app inactivity, on a 24h scale clipped to the active span.
 * Idle is shown separately from breaks and is never coloured as a break.
 */
export function ShiftTimeline({ data, compact = false, className }: { data: TimelineInput; compact?: boolean; className?: string }) {
  const reduce = useReducedMotion();
  const tz = data.timezone;
  const now = new Date().toISOString();
  const span = (start: string, end?: string): [number, number] => {
    const s = dateIn(start, tz) < data.date ? 0 : minuteOfDay(start, tz);
    const endIso = end ?? now;
    const e = dateIn(endIso, tz) > data.date ? 1440 : minuteOfDay(endIso, tz);
    return [s, Math.max(e, s + 1)];
  };
  const work = data.sessions.map((s) => ({ r: span(s.loginAt, s.logoutAt), s }));
  const brk = data.breaks.map((b) => ({ r: span(b.startedAt, b.endedAt), b }));
  const idl = data.idles.map((i) => ({ r: span(i.startedAt, i.endedAt), i }));
  const all = [...work.map((w) => w.r), ...brk.map((b) => b.r)];
  let lo = all.length ? Math.min(...all.map((r) => r[0])) : 8 * 60;
  let hi = all.length ? Math.max(...all.map((r) => r[1])) : 18 * 60;
  lo = Math.max(0, Math.floor((lo - 30) / 60) * 60);
  hi = Math.min(1440, Math.ceil((hi + 30) / 60) * 60);
  if (hi - lo < 240) hi = Math.min(1440, lo + 240);
  const pct = (m: number) => ((Math.min(Math.max(m, lo), hi) - lo) / (hi - lo)) * 100;
  const ticks: number[] = [];
  const step = hi - lo > 720 ? 180 : hi - lo > 360 ? 120 : 60;
  for (let t = lo; t <= hi; t += step) ticks.push(t);

  return (
    <div className={cn('w-full', className)}>
      <div className={cn('relative w-full overflow-hidden rounded-full bg-muted', compact ? 'h-3' : 'h-9 dot-grid')} role="img"
        aria-label={`Timeline for ${data.date}: ${data.sessions.length} session(s), ${data.breaks.length} break(s), ${data.idles.length} inactivity event(s)`}>
        {work.map(({ r, s }, i) => (
          <Tooltip key={'w' + i} content={`Signed in ${time(s.loginAt, tz)} – ${s.logoutAt ? time(s.logoutAt, tz) : 'now'}${s.status === 'MISSING_LOGOUT' ? ' (missing logout)' : ''}`}>
            <motion.div
              initial={reduce ? false : { scaleX: 0 }} animate={{ scaleX: 1 }} transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1], delay: i * 0.05 }}
              style={{ left: `${pct(r[0])}%`, width: `${pct(r[1]) - pct(r[0])}%`, transformOrigin: 'left' }}
              className={cn('absolute inset-y-0 rounded-full bg-brand', s.status === 'MISSING_LOGOUT' && 'bg-[repeating-linear-gradient(45deg,hsl(var(--primary)/.75)_0_6px,hsl(var(--primary)/.45)_6px_12px)]')} />
          </Tooltip>
        ))}
        {idl.map(({ r, i }, k) => (
          <Tooltip key={'i' + k} content={`No app activity ${time(i.startedAt, tz)} – ${i.endedAt ? time(i.endedAt, tz) : 'now'}${i.response ? ` · answered “${i.response.toLowerCase().replace('_', ' ')}”` : ' · unconfirmed'}`}>
            <div style={{ left: `${pct(r[0])}%`, width: `${pct(r[1]) - pct(r[0])}%` }}
              className="absolute inset-y-0 bg-[repeating-linear-gradient(90deg,hsl(var(--idle)/.9)_0_2px,transparent_2px_5px)]" />
          </Tooltip>
        ))}
        {brk.map(({ r, b }, k) => (
          <Tooltip key={'b' + k} content={`${b.category ? b.category.charAt(0) + b.category.slice(1).toLowerCase() + ' break' : 'Break'} ${time(b.startedAt, tz)} – ${b.endedAt ? time(b.endedAt, tz) : 'now'} · ${duration(b.durationSeconds)}${b.exceeded ? ' · over limit' : ''}`}>
            <div style={{ left: `${pct(r[0])}%`, width: `${pct(r[1]) - pct(r[0])}%` }}
              className={cn('absolute inset-y-[3px] rounded-full ring-2 ring-card', b.exceeded ? 'bg-breach' : 'bg-warn')} />
          </Tooltip>
        ))}
      </div>
      {!compact && (
        <div className="relative mt-1 h-4 text-[10px] text-muted-foreground num">
          {ticks.map((t) => (
            <span key={t} className="absolute -translate-x-1/2" style={{ left: `${pct(t)}%` }}>{String(Math.floor(t / 60)).padStart(2, '0')}:00</span>
          ))}
        </div>
      )}
    </div>
  );
}

export function TimelineLegend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
      <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-full bg-brand" />Signed in</span>
      <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-full bg-warn" />Break</span>
      <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-full bg-breach" />Break over limit</span>
      <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-full bg-[repeating-linear-gradient(90deg,hsl(var(--idle))_0_2px,transparent_2px_5px)]" />No app activity</span>
    </div>
  );
}
