import { useState } from 'react';
import { Coffee, Play, Square } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { NativeSelect } from '@/components/ui/input';
import { useAttendance } from '@/contexts/AttendanceContext';
import { useNow } from '@/hooks/useNow';
import { clock } from '@/utils/format';
import { cn } from '@/utils/cn';

/** Compact break control used in the top bar. */
export function BreakPill() {
  const { today, endBreak, busy } = useAttendance();
  const now = useNow(1000);
  const b = today?.currentBreak;
  if (!b) return null;
  const secs = (now - new Date(b.startedAt).getTime()) / 1000;
  const over = b.limitMinutes != null && secs > b.limitMinutes * 60;
  return (
    <div className={cn('hidden items-center gap-2 rounded-full border py-1 pl-3 pr-1 text-xs sm:flex', over ? 'border-breach/40 bg-breach/10 text-breach' : 'border-warn/40 bg-warn/10 text-warn')}>
      <Coffee className="h-3.5 w-3.5" />
      <span className="num font-medium">On break {clock(secs)}</span>
      <Button size="sm" variant="outline" className="h-6 rounded-full px-2 text-xs" loading={busy} onClick={() => void endBreak()}>End</Button>
    </div>
  );
}

/** Full break control used on the attendance page. */
export function BreakControl() {
  const { today, startBreak, endBreak, busy } = useAttendance();
  const now = useNow(1000);
  const [category, setCategory] = useState('GENERAL');
  if (!today) return null;
  const b = today.currentBreak;
  if (b) {
    const secs = (now - new Date(b.startedAt).getTime()) / 1000;
    const limit = b.limitMinutes;
    const over = limit != null && secs > limit * 60;
    const pct = limit ? Math.min(100, (secs / (limit * 60)) * 100) : null;
    return (
      <div className="space-y-3">
        <div className="flex items-baseline justify-between gap-4">
          <div>
            <p className="text-sm text-muted-foreground">Current break · {today.breakCategories.find((c) => c.code === b.category)?.label ?? b.category}</p>
            <p className={cn('num text-4xl font-semibold tracking-tight', over ? 'text-breach' : 'text-foreground')} aria-live="polite">{clock(secs)}</p>
          </div>
          <Button size="lg" variant={over ? 'destructive' : 'default'} loading={busy} onClick={() => void endBreak()}><Square /> End break</Button>
        </div>
        {pct != null && (
          <div>
            <div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className={cn('h-full transition-[width]', over ? 'bg-breach' : 'bg-warn')} style={{ width: `${pct}%` }} /></div>
            <p className="mt-1 text-xs text-muted-foreground">{over ? `Over the ${limit}-minute limit` : `Limit ${limit} minutes`}</p>
          </div>
        )}
      </div>
    );
  }
  const offline = !today.currentSession || today.currentState === 'OFFLINE';
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
      <div className="flex-1 space-y-1.5">
        <label htmlFor="break-category" className="text-sm font-medium">Break type</label>
        <NativeSelect id="break-category" value={category} onChange={(e) => setCategory(e.target.value)} disabled={offline}>
          {today.breakCategories.map((c) => <option key={c.code} value={c.code}>{c.label}</option>)}
        </NativeSelect>
      </div>
      <Button size="lg" loading={busy} disabled={offline} onClick={() => void startBreak(category)}><Play /> Start break</Button>
    </div>
  );
}
