import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, Bell, Coffee, Mail, Moon, Ticket, Timer } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { motion } from 'motion/react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { IconTile, gridItem, gridParent } from '@/components/layout';
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
      {loading && !data ? <Card><TableSkeleton /></Card> : data && (data.content.length === 0 ? <Card><EmptyState title="You're all caught up" /></Card> : (
        <div className="space-y-4">
          {group(data.content).map(([label, items]) => (
            <Card key={label}>
              <CardHeader><CardTitle>{label}</CardTitle><span className="text-xs text-muted-foreground">{items.filter((n) => !n.readAt).length} unread</span></CardHeader>
              <CardContent>
                <motion.ul className="space-y-1.5" variants={gridParent} initial="hidden" animate="show">
                  {items.map((n) => {
                    const [Icon, tone] = iconFor(n);
                    return (
                      <motion.li key={n.id} variants={gridItem}>
                        <button onClick={() => open(n)} className={`flex w-full items-start gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-accent/60 ${n.readAt ? '' : 'bg-primary/5 ring-1 ring-primary/15'}`}>
                          <IconTile icon={Icon} tone={tone} />
                          <div className="min-w-0 flex-1">
                            <p className={`text-sm ${n.readAt ? '' : 'font-semibold'}`}>{n.title}</p>
                            {n.message && <p className="text-sm text-muted-foreground">{n.message}</p>}
                            <p className="mt-0.5 text-xs text-muted-foreground">{dateTime(n.createdAt)} · {titleCase(n.type)}</p>
                          </div>
                          <div className="flex shrink-0 flex-col items-end gap-1.5">
                            {n.severity !== 'INFO' && <Badge tone={n.severity === 'CRITICAL' ? 'red' : 'amber'}>{titleCase(n.severity)}</Badge>}
                            {!n.readAt && <span className="h-2.5 w-2.5 rounded-full bg-primary shadow-glow" aria-label="Unread" />}
                          </div>
                        </button>
                      </motion.li>
                    );
                  })}
                </motion.ul>
              </CardContent>
            </Card>
          ))}
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </div>
      ))}
    </>
  );
}

function group(items: N[]): [string, N[]][] {
  const start = new Date(); start.setHours(0, 0, 0, 0);
  const y = new Date(start.getTime() - 86400000);
  const g: [string, N[]][] = [['Today', []], ['Yesterday', []], ['Earlier', []]];
  for (const n of items) { const d = new Date(n.createdAt); g[d >= start ? 0 : d >= y ? 1 : 2][1].push(n); }
  return g.filter(([, v]) => v.length);
}

function iconFor(n: N): [LucideIcon, string] {
  if (n.severity === 'CRITICAL') return [AlertTriangle, 'coral'];
  if (/SLA/.test(n.type)) return [Timer, 'amber'];
  if (/BREAK/.test(n.type)) return [Coffee, 'amber'];
  if (/IDLE|INACTIV/.test(n.type)) return [Moon, 'sky'];
  if (/REPORT|EMAIL/.test(n.type)) return [Mail, 'mint'];
  if (/TICKET|ASSIGN/.test(n.type)) return [Ticket, 'brand'];
  return [Bell, 'soft'];
}
