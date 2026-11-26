import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { FilterBar, SearchField } from '@/components/layout';
import { Input } from '@/components/ui/input';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Avatar, EmptyState, ErrorState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { time, titleCase } from '@/utils/format';
import type { Page } from '@/types/api';

interface Row { id: string; actorUsername?: string; action: string; entityType?: string; entityId?: string; details?: string; ipAddress?: string; correlationId?: string; createdAt: string }

export default function AuditPage() {
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi<Page<Row>>('/audit', { action: action || undefined, entityType: entityType || undefined, page, size: 50 });
  return (
    <>
      <PageHeader title="Audit trail" description="Every administrative and security-relevant change, with who made it, from where and when." />
      <FilterBar>
        <SearchField label="Action contains" placeholder="Action, e.g. SESSION…" value={action} onChange={(v) => { setPage(0); setAction(v); }} className="sm:w-64" />
        <Input className="w-48 rounded-full" aria-label="Entity type" placeholder="Entity, e.g. Project…" value={entityType} onChange={(e) => { setPage(0); setEntityType(e.target.value); }} />
      </FilterBar>
      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? <Card><TableSkeleton /></Card> : data && (data.content.length === 0 ? <Card><EmptyState title="No audit entries match" /></Card> : (
        <div className="space-y-4">
          {groupByDay(data.content).map(([day, rows]) => (
            <Card key={day}>
              <CardHeader><CardTitle>{day}</CardTitle><span className="text-xs text-muted-foreground">{rows.length} change{rows.length === 1 ? '' : 's'}</span></CardHeader>
              <CardContent>
                <ol className="relative ml-4 space-y-1 border-l border-dashed border-border pl-6">
                  {rows.map((a) => (
                    <li key={a.id} className="relative rounded-xl px-3 py-2.5 transition-colors hover:bg-accent/60">
                      <span className={`absolute -left-[31px] top-4 h-3 w-3 rounded-full ring-4 ring-card ${dotFor(a.action)}`} />
                      <div className="flex flex-wrap items-center gap-2">
                        <Avatar name={a.actorUsername ?? 'system'} className="h-7 w-7 text-[9px]" />
                        <span className="text-sm font-semibold">{a.actorUsername ?? 'system'}</span>
                        <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">{titleCase(a.action)}</span>
                        {a.entityType && <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs">{a.entityType}{a.entityId ? <span className="ml-1 font-mono text-muted-foreground">{a.entityId.slice(0, 8)}</span> : null}</span>}
                        <span className="ml-auto display-num text-xs text-muted-foreground">{time(a.createdAt)}</span>
                      </div>
                      {a.details && <p className="mt-1 pl-9 text-xs text-muted-foreground">{a.details}</p>}
                      {a.ipAddress && <p className="mt-1 pl-9"><span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px]">{a.ipAddress}</span></p>}
                    </li>
                  ))}
                </ol>
              </CardContent>
            </Card>
          ))}
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </div>
      ))}
    </>
  );
}

function groupByDay(rows: Row[]): [string, Row[]][] {
  const m = new Map<string, Row[]>();
  for (const r of rows) {
    const k = new Date(r.createdAt).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    m.set(k, [...(m.get(k) ?? []), r]);
  }
  return [...m.entries()];
}

function dotFor(action: string) {
  if (/DELETE|TERMINAT|LOCK|DENY|FAIL/.test(action)) return 'bg-breach';
  if (/CREATE|ADD/.test(action)) return 'bg-ok';
  if (/CORRECT|RESET|UPDATE|CHANGE/.test(action)) return 'bg-warn';
  return 'bg-primary';
}
