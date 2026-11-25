import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, TokenResponse } from '@/types/api';

/**
 * Access token lives only in memory. The refresh token is an HttpOnly, SameSite=Strict cookie scoped to /api/v1/auth,
 * so JavaScript never sees it. A 401 triggers one refresh (rotating the cookie) and the request is retried.
 */
let accessToken: string | null = null;
let refreshing: Promise<TokenResponse | null> | null = null;
const listeners = new Set<(t: TokenResponse | null) => void>();

export const api = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 30000 });

export function setAccessToken(token: string | null) {
  accessToken = token;
}
export function getAccessToken() {
  return accessToken;
}
export function onSessionChange(fn: (t: TokenResponse | null) => void) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export async function refreshSession(): Promise<TokenResponse | null> {
  if (!refreshing) {
    refreshing = axios
      .post<TokenResponse>('/api/v1/auth/refresh', null, { withCredentials: true })
      .then((r) => {
        setAccessToken(r.data.accessToken);
        listeners.forEach((l) => l(r.data));
        return r.data;
      })
      .catch(() => {
        setAccessToken(null);
        listeners.forEach((l) => l(null));
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`);
  return config;
});

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const url = original?.url ?? '';
    if (error.response?.status === 401 && original && !original._retry && !url.startsWith('/auth/')) {
      original._retry = true;
      const t = await refreshSession();
      if (t) {
        original.headers.set('Authorization', `Bearer ${t.accessToken}`);
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);

export function errorMessage(e: unknown, fallback = 'Something went wrong. Try again.'): string {
  const ax = e as AxiosError<ApiError>;
  const data = ax?.response?.data;
  if (data?.fieldErrors && Object.keys(data.fieldErrors).length) {
    return Object.entries(data.fieldErrors).map(([k, v]) => `${k}: ${v}`).join('; ');
  }
  if (data?.message) return data.message;
  if (ax?.code === 'ERR_NETWORK') return 'The server could not be reached. Check your connection.';
  return fallback;
}

export async function download(url: string, params?: Record<string, unknown>) {
  const res = await api.get(url, { params, responseType: 'blob' });
  const disposition = res.headers['content-disposition'] as string | undefined;
  const match = disposition?.match(/filename="?([^";]+)"?/);
  const name = match?.[1] ?? 'download';
  const href = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}
