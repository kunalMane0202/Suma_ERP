import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, onSessionChange, refreshSession, setAccessToken } from '@/services/api';
import type { Me, TokenResponse } from '@/types/api';

interface AuthState {
  user: Me | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<Me>;
  logout: () => Promise<void>;
  can: (permission: string) => boolean;
  canAny: (...permissions: string[]) => boolean;
  updateUser: (u: Me) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const off = onSessionChange((t: TokenResponse | null) => setUser(t ? t.user : null));
    refreshSession().finally(() => setReady(true));
    return () => {
      off();
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const r = await api.post<TokenResponse>('/auth/login', { username, password });
    setAccessToken(r.data.accessToken);
    setUser(r.data.user);
    return r.data.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthState>(() => ({
    user,
    ready,
    login,
    logout,
    can: (p) => !!user?.permissions.includes(p),
    canAny: (...ps) => ps.some((p) => user?.permissions.includes(p)),
    updateUser: setUser,
  }), [user, ready, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
