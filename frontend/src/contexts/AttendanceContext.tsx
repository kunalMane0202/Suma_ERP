import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/services/api';
import { useAuth } from '@/contexts/AuthContext';
import type { BreakDto, TodayView } from '@/types/attendance';

interface AttendanceState {
  today: TodayView | null;
  refresh: () => Promise<void>;
  startBreak: (category?: string, notes?: string) => Promise<BreakDto | null>;
  endBreak: () => Promise<BreakDto | null>;
  busy: boolean;
}

const Ctx = createContext<AttendanceState | null>(null);

export function AttendanceProvider({ children }: { children: ReactNode }) {
  const { user, can } = useAuth();
  const enabled = !!user && can('ATTENDANCE_SELF');
  const [today, setToday] = useState<TodayView | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    if (!enabled) return;
    try {
      const r = await api.get<TodayView>('/attendance/me');
      setToday(r.data);
    } catch {
      /* surfaced on the attendance page */
    }
  }, [enabled]);

  useEffect(() => {
    void refresh();
    if (!enabled) return;
    const h = window.setInterval(() => void refresh(), 60_000);
    return () => window.clearInterval(h);
  }, [refresh, enabled]);

  const startBreak = useCallback(async (category?: string, notes?: string) => {
    setBusy(true);
    try {
      const r = await api.post<BreakDto>('/attendance/breaks/start', { category, notes });
      toast.success('Break started');
      await refresh();
      return r.data;
    } catch (e) {
      toast.error(errorMessage(e));
      return null;
    } finally {
      setBusy(false);
    }
  }, [refresh]);

  const endBreak = useCallback(async () => {
    setBusy(true);
    try {
      const r = await api.post<BreakDto>('/attendance/breaks/end');
      toast.success(r.data.exceeded ? 'Break ended — it went over the configured limit' : 'Break ended');
      await refresh();
      return r.data;
    } catch (e) {
      toast.error(errorMessage(e));
      return null;
    } finally {
      setBusy(false);
    }
  }, [refresh]);

  return <Ctx.Provider value={{ today, refresh, startBreak, endBreak, busy }}>{children}</Ctx.Provider>;
}

export function useAttendance() {
  const c = useContext(Ctx);
  if (!c) throw new Error('useAttendance must be used inside AttendanceProvider');
  return c;
}
