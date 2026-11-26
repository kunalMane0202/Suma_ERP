import { Search } from 'lucide-react';
import { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { dateTime, titleCase } from '@/utils/format';
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
      <Card>
        <div className="flex flex-wrap gap-2 p-4">
          <div className="relative w-full sm:w-64"><Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" /><Input className="pl-8" aria-label="Action contains" placeholder="Action, e.g. SESSION" value={action} onChange={(e) => { setPage(0); setAction(e.target.value); }} /></div>
          <Input className="w-48" aria-label="Entity type" placeholder="Entity, e.g. Project" value={entityType} onChange={(e) => { setPage(0); setEntityType(e.target.value); }} />
        </div>
        {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No audit entries match" /> : (
          <>
            <Table>
              <THead><TR><TH>When</TH><TH>Who</TH><TH>Action</TH><TH>Record</TH><TH>Details</TH><TH>IP</TH></TR></THead>
              <TBody>{data.content.map((a) => (
                <TR key={a.id}><TD className="num whitespace-nowrap text-xs">{dateTime(a.createdAt)}</TD><TD>{a.actorUsername}</TD><TD className="text-sm font-medium">{titleCase(a.action)}</TD>
                  <TD className="text-xs">{a.entityType}<span className="block font-mono text-muted-foreground">{a.entityId?.slice(0, 13)}</span></TD>
                  <TD className="max-w-md text-xs text-muted-foreground">{a.details}</TD><TD className="font-mono text-xs">{a.ipAddress}</TD></TR>
              ))}</TBody>
            </Table>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ))}
      </Card>
    </>
  );
}
