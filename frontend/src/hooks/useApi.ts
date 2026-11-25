import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage } from '@/services/api';

/** Small data-fetching hook: loads on mount and whenever the key changes; exposes reload(). */
export function useApi<T>(url: string | null, params?: Record<string, unknown>, options?: { interval?: number }) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(!!url);
  const key = url ? url + JSON.stringify(params ?? {}) : null;
  const seq = useRef(0);

  const load = useCallback(async (silent = false) => {
    if (!url) return;
    const id = ++seq.current;
    if (!silent) setLoading(true);
    try {
      const r = await api.get<T>(url, { params });
      if (id === seq.current) {
        setData(r.data);
        setError(null);
      }
    } catch (e) {
      if (id === seq.current) setError(errorMessage(e));
    } finally {
      if (id === seq.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!options?.interval) return;
    const h = window.setInterval(() => void load(true), options.interval);
    return () => window.clearInterval(h);
  }, [load, options?.interval]);

  return { data, error, loading, reload: () => load(true), setData };
}
