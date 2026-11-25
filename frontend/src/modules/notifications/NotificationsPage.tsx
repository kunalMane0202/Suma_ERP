import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
import { TableSkeleton } from '@/components/ui/skeleton';
import { EmptyState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { api } from '@/services/api';
import { dateTime, titleCase } from '@/utils/format';
import type { Page } from '@/types/api';

interface N { id: string; type: string; title: string; message?: string; severity: string; entityType?: string; entityId?: string; readAt?: string; createdAt: string }

export default function NotificationsPage() {
  const navigate = useNavigate();
  const [unread, setUnread] = useState(false);
  const [page, setPage] = useState(0);
  const { data, loading, reload } = useApi<Page<N>>('/notifications', { unread, page, size: 25 });
  const open = async (n: N) => {
    if (!n.readAt) await api.post(`/notifications/${n.id}/read`);
    if (n.entityType === 'Ticket' && n.entityId) navigate(`/tickets/${n.entityId}`); else reload();
  };
  return (
    <>
      <PageHeader title="Notifications" description="Ticket assignments, SLA alerts, break reminders, inactivity prompts and report deliveries."
        actions={<><label className="flex items-center gap-2 text-sm"><Switch checked={unread} onCheckedChange={(v) => { setPage(0); setUnread(v); }} /> Unread only</label>
          <Button variant="outline" onClick={async () => { await api.post('/notifications/read-all'); reload(); }}>Mark all read</Button></>} />
      <Card>
        {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="You're all caught up" /> : (
          <>
            <ul className="divide-y">
              {data.content.map((n) => (
                <li key={n.id}>
                  <button onClick={() => open(n)} className="flex w-full items-start gap-3 px-5 py-3 text-left hover:bg-accent/40">
                    <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.readAt ? 'bg-transparent' : 'bg-primary'}`} aria-label={n.readAt ? undefined : 'Unread'} />
                    <div className="min-w-0 flex-1">
                      <p className={`text-sm ${n.readAt ? '' : 'font-semibold'}`}>{n.title}</p>
                      {n.message && <p className="text-sm text-muted-foreground">{n.message}</p>}
                      <p className="mt-0.5 text-xs text-muted-foreground">{dateTime(n.createdAt)} · {titleCase(n.type)}</p>
                    </div>
                    {n.severity !== 'INFO' && <Badge tone={n.severity === 'CRITICAL' ? 'red' : 'amber'}>{titleCase(n.severity)}</Badge>}
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ))}
      </Card>
    </>
  );
}
