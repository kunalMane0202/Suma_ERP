import { useEffect, useState } from 'react';
import { api } from '@/services/api';
import { useAuth } from '@/contexts/AuthContext';
import type { Client, Page, Project, Team, UserSummary } from '@/types/api';

interface Lookups { clients: Client[]; projects: Project[]; teams: Team[]; users: UserSummary[] }
let cache: { at: number; data: Lookups } | null = null;

/** Reference lists for filters and pickers, cached for a minute and scoped by the server. */
export function useLookups() {
  const { can, user } = useAuth();
  const [data, setData] = useState<Lookups>(cache?.data ?? { clients: [], projects: [], teams: [], users: [] });
  useEffect(() => {
    if (cache && Date.now() - cache.at < 60_000) { setData(cache.data); return; }
    let live = true;
    Promise.all([
      can('CLIENT_READ') ? api.get<Page<Client>>('/clients', { params: { size: 200 } }).then((r) => r.data.content).catch(() => []) : Promise.resolve([]),
      can('PROJECT_READ') ? api.get<Page<Project>>('/projects', { params: { size: 200 } }).then((r) => r.data.content).catch(() => []) : Promise.resolve([]),
      can('TEAM_READ') ? api.get<Team[]>('/teams').then((r) => r.data).catch(() => []) : Promise.resolve([]),
      api.get<UserSummary[]>('/users/directory').then((r) => r.data).catch(() => []),
    ]).then(([clients, projects, teams, users]) => {
      const d = { clients, projects, teams, users } as Lookups;
      cache = { at: Date.now(), data: d };
      if (live) setData(d);
    });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);
  return data;
}

export function invalidateLookups() {
  cache = null;
}
