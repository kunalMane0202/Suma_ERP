import { useCallback, useEffect, useRef, useState } from 'react';
import { Coffee, Laptop, Moon } from 'lucide-react';
import { toast } from 'sonner';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { api, errorMessage } from '@/services/api';
import { useAttendance } from '@/contexts/AttendanceContext';
import type { IdleDto, IdleStatus } from '@/types/attendance';

const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'mousemove', 'scroll'] as const;
const HEARTBEAT_MS = 60_000;
const CHECK_MS = 15_000;

/**
 * In-app activity monitoring. Only interaction with this SUMA tab is observed (keyboard, mouse, touch, scrolling,
 * page visibility); no keystroke content is captured. When inactivity reaches the configured threshold the employee is
 * asked to confirm their status. Unanswered prompts remain "unconfirmed" — they are never converted into breaks.
 */
export function ActivityMonitor() {
  const { today, refresh } = useAttendance();
  const lastActivity = useRef<number>(Date.now());
  const thresholdMin = useRef<number>(20);
  const [prompt, setPrompt] = useState<IdleDto | null>(null);
  const [answering, setAnswering] = useState<string | null>(null);
  const reporting = useRef(false);
  const onBreak = today?.currentState === 'ON_BREAK';
  const onBreakRef = useRef(onBreak);
  onBreakRef.current = onBreak;
  const promptRef = useRef<IdleDto | null>(null);
  promptRef.current = prompt;

  useEffect(() => {
    let lastMove = 0;
    const mark = (e: Event) => {
      if (e.type === 'mousemove') {
        const t = Date.now();
        if (t - lastMove < 2000) return;
        lastMove = t;
      }
      if (!promptRef.current) lastActivity.current = Date.now();
    };
    const vis = () => { if (document.visibilityState === 'visible' && !promptRef.current) lastActivity.current = Date.now(); };
    ACTIVITY_EVENTS.forEach((ev) => window.addEventListener(ev, mark, { passive: true }));
    document.addEventListener('visibilitychange', vis);
    return () => {
      ACTIVITY_EVENTS.forEach((ev) => window.removeEventListener(ev, mark));
      document.removeEventListener('visibilitychange', vis);
    };
  }, []);

  const applyStatus = useCallback((s: IdleStatus) => {
    thresholdMin.current = s.thresholdMinutes;
    if (s.openIdle && !s.openIdle.response && !promptRef.current) setPrompt(s.openIdle);
  }, []);

  useEffect(() => {
    api.get<IdleStatus>('/attendance/idle/status').then((r) => applyStatus(r.data)).catch(() => undefined);
    const beat = window.setInterval(() => {
      api.post<IdleStatus>('/attendance/heartbeat', {
        lastActivityAt: new Date(lastActivity.current).toISOString(),
        visible: document.visibilityState === 'visible',
      }).then((r) => applyStatus(r.data)).catch(() => undefined);
    }, HEARTBEAT_MS);
    const check = window.setInterval(async () => {
      if (promptRef.current || reporting.current || onBreakRef.current) return;
      const idleMs = Date.now() - lastActivity.current;
      if (idleMs < thresholdMin.current * 60_000) return;
      reporting.current = true;
      try {
        const r = await api.post<IdleDto>('/attendance/idle/events', { idleStartedAt: new Date(lastActivity.current).toISOString() });
        if (!r.data.response) setPrompt(r.data);
      } catch {
        /* session may have ended; the shell will redirect */
      } finally {
        reporting.current = false;
      }
    }, CHECK_MS);
    return () => {
      window.clearInterval(beat);
      window.clearInterval(check);
    };
  }, [applyStatus]);

  const answer = async (response: 'WORKING' | 'ON_BREAK' | 'AWAY') => {
    if (!prompt) return;
    setAnswering(response);
    try {
      await api.post('/attendance/idle/confirm', { idleEventId: prompt.id, response });
      lastActivity.current = Date.now();
      setPrompt(null);
      toast.success(response === 'WORKING' ? 'Thanks — marked as working' : response === 'ON_BREAK' ? 'Break started' : 'Marked as away');
      await refresh();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setAnswering(null);
    }
  };

  const minutes = prompt ? Math.max(1, Math.round((Date.now() - new Date(prompt.idleStartedAt).getTime()) / 60000)) : 0;
  return (
    <Dialog open={!!prompt} onOpenChange={() => undefined} dismissible={false}
      title="Are you still working?"
      description={`There has been no activity in SUMA for about ${minutes} minutes. Let us know your status so your attendance is recorded correctly.`}>
      <div className="grid gap-2 sm:grid-cols-3">
        <Button variant="default" className="h-auto flex-col gap-1 py-3" loading={answering === 'WORKING'} onClick={() => answer('WORKING')}>
          <Laptop /> I'm working
        </Button>
        <Button variant="outline" className="h-auto flex-col gap-1 py-3" loading={answering === 'ON_BREAK'} onClick={() => answer('ON_BREAK')}>
          <Coffee /> I'm on a break
        </Button>
        <Button variant="outline" className="h-auto flex-col gap-1 py-3" loading={answering === 'AWAY'} onClick={() => answer('AWAY')}>
          <Moon /> I'm away
        </Button>
      </div>
      <p className="mt-4 text-xs text-muted-foreground">
        Only activity inside this SUMA tab is checked. Inactivity is recorded separately from breaks and is not treated as a break unless you choose so.
      </p>
    </Dialog>
  );
}
