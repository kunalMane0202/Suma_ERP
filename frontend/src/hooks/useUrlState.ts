import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * A string value kept in the page address (?key=value), so filters, tabs and pages survive reloads and can be shared.
 * The fallback is not written to the URL; updates replace the history entry instead of adding one per keystroke.
 */
export function useUrlState(key: string, fallback = ''): [string, (v: string) => void] {
  const [sp, setSp] = useSearchParams();
  const value = sp.get(key) ?? fallback;
  const set = useCallback((v: string) => {
    // Read the live address (not the render-time snapshot) so several updates in one event all apply.
    setSp(() => {
      const next = new URLSearchParams(window.location.search);
      if (v === fallback || v === '') next.delete(key); else next.set(key, v);
      return next;
    }, { replace: true });
  }, [key, fallback, setSp]);
  return [value, set];
}

/** Number variant (e.g. page index). */
export function useUrlNumber(key: string, fallback = 0): [number, (v: number) => void] {
  const [raw, set] = useUrlState(key, String(fallback));
  const n = Number(raw);
  return [Number.isFinite(n) && n >= 0 ? n : fallback, useCallback((v: number) => set(String(v)), [set])];
}
