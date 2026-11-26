import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Can, EmptyState, ErrorState, PageHeader, Pagination, PriorityTag, SlaBadge, StatusBadge } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, errorMessage } from '@/services/api';
import { relative } from '@/utils/format';
import type { Category, Page, TicketDetail, TicketSummary } from '@/types/api';

const STRIPE: Record<string, string> = { CRITICAL: 'bg-breach', HIGH: 'bg-[#F27A3D]', MEDIUM: 'bg-warn', LOW: 'bg-ok' };
const OPEN = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING', 'REOPENED'];
const VIEWS = [
  { key: 'open', label: 'Open' }, { key: 'mine', label: 'Assigned to me' }, { key: 'unassigned', label: 'Unassigned' },
  { key: 'overdue', label: 'Overdue' }, { key: 'breached', label: 'SLA breached' }, { key: 'all', label: 'All' },
];

export default function TicketsPage() {
  const [sp, setSp] = useSearchParams();
  const lk = useLookups();
  const view = sp.get('overdue') ? 'overdue' : sp.get('view') ?? 'open';
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  const [priority, setPriority] = useState('');
  const [projectId, setProjectId] = useState(sp.get('projectId') ?? '');
  const [sort, setSort] = useState('newest');
  const [creating, setCreating] = useState(false);
  useEffect(() => { const h = setTimeout(() => setDebounced(q), 300); return () => clearTimeout(h); }, [q]);
  useEffect(() => setPage(0), [view, debounced, priority, projectId]);

  const params = useMemo(() => {
    const p: Record<string, unknown> = { page, size: 25, sort, q: debounced || undefined, priority: priority || undefined, projectId: projectId || undefined };
    if (view === 'open') p.status = OPEN.join(',');
    if (view === 'mine') { p.mine = true; p.status = OPEN.join(','); }
    if (view === 'unassigned') { p.unassigned = true; p.status = OPEN.join(','); }
    if (view === 'overdue') p.overdue = true;
    if (view === 'breached') p.breached = true;
    return p;
  }, [view, page, sort, debounced, priority, projectId]);
  const { data, error, loading, reload } = useApi<Page<TicketSummary>>('/tickets', params, { interval: 60_000 });

  return (
    <>
      <PageHeader title="Tickets" description="Every ticket you can work on, with its SLA clock."
        actions={<Can permission="TICKET_CREATE"><Button onClick={() => setCreating(true)}><Plus /> New ticket</Button></Can>} />
      <Card>
        <div className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <div className="flex flex-wrap gap-1" role="tablist" aria-label="Ticket views">
            {VIEWS.map((v) => (
              <button key={v.key} role="tab" aria-selected={view === v.key} onClick={() => setSp(v.key === 'open' ? {} : { view: v.key })}
                className={`rounded-full px-3.5 py-1.5 text-[13px] font-semibold transition ${view === v.key ? 'bg-brand text-white shadow-glow' : 'text-muted-foreground hover:bg-accent hover:text-foreground'}`}>{v.label}</button>
            ))}
          </div>
          <div className="flex flex-1 flex-wrap items-center gap-2 lg:justify-end">
            <div className="relative w-full sm:w-64">
              <Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input className="pl-8" placeholder="Search number or title" aria-label="Search tickets" value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            {lk.projects.length > 0 && <NativeSelect aria-label="Project" className="w-44" value={projectId} onChange={(e) => setProjectId(e.target.value)}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect>}
            <NativeSelect aria-label="Priority" className="w-36" value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="">Any priority</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Sort" className="w-40" value={sort} onChange={(e) => setSort(e.target.value)}>
              <option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="due">Due soonest</option><option value="priority">Priority</option><option value="updated">Recently updated</option>
            </NativeSelect>
          </div>
        </div>
        {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton rows={8} cols={6} /> : data && (
          data.content.length === 0 ? <EmptyState title="No tickets in this view" description="Try another view or clear the filters." /> : (
            <>
              <Table>
                <THead><TR><TH>Ticket</TH><TH>Priority</TH><TH>Status</TH><TH>SLA</TH><TH>Assignee</TH><TH>Project</TH><TH>Age</TH></TR></THead>
                <TBody>
                  {data.content.map((t) => (
                    <TR key={t.id}>
                      <TD className="relative max-w-[28rem] pl-5">
                        <span className={`absolute inset-y-2 left-1.5 w-1 rounded-full ${STRIPE[t.priority]}`} aria-hidden />
                        <Link to={`/tickets/${t.id}`} className="font-medium hover:text-primary hover:underline">{t.title}</Link>
                        <p className="num text-xs text-muted-foreground">{t.ticketNumber}{t.categoryName ? ` · ${t.categoryName}` : ''}{t.escalationLevel > 0 ? ` · escalated L${t.escalationLevel}` : ''}</p>
                      </TD>
                      <TD><PriorityTag priority={t.priority} /></TD>
                      <TD><StatusBadge status={t.status} /></TD>
                      <TD><SlaBadge sla={t.sla} /></TD>
                      <TD className="text-sm">{t.assigneeName ?? <span className="text-muted-foreground">Unassigned</span>}</TD>
                      <TD className="text-xs text-muted-foreground">{t.projectName}<br />{t.clientName}</TD>
                      <TD className="num whitespace-nowrap text-xs text-muted-foreground" title={t.createdAt}>{relative(t.createdAt)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
              <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
            </>
          ))}
      </Card>
      <CreateTicketDialog open={creating} onOpenChange={setCreating} />
    </>
  );
}

const schema = z.object({
  projectId: z.string().uuid('Choose a project'),
  title: z.string().trim().min(5, 'Use at least 5 characters').max(300),
  description: z.string().max(20000).optional(),
  priority: z.enum(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']),
  categoryId: z.string().optional(),
  reporterName: z.string().max(150).optional(),
  reporterEmail: z.union([z.literal(''), z.string().email('Enter a valid email')]).optional(),
});
type Form = z.infer<typeof schema>;

export function CreateTicketDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const lk = useLookups();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [cats, setCats] = useState<Category[]>([]);
  const projects = lk.projects.length ? lk.projects.filter((p) => p.status === 'ACTIVE') : (user?.projects ?? []).map((p) => ({ id: p.id, name: p.name }));
  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { priority: 'MEDIUM' } });
  const projectId = watch('projectId');
  useEffect(() => {
    if (!projectId) { setCats([]); return; }
    api.get<Category[]>(`/projects/${projectId}/categories`).then((r) => setCats(r.data)).catch(() => setCats([]));
  }, [projectId]);
  const submit = async (v: Form) => {
    try {
      const r = await api.post<TicketDetail>('/tickets', { ...v, categoryId: v.categoryId || undefined, reporterEmail: v.reporterEmail || undefined });
      toast.success(`Ticket ${r.data.summary.ticketNumber} created`);
      reset();
      onOpenChange(false);
      navigate(`/tickets/${r.data.summary.id}`);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="New ticket" description="The SLA clock starts when the ticket is created." className="max-w-xl"
      footer={<><Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button><Button loading={isSubmitting} onClick={handleSubmit(submit)}>Create ticket</Button></>}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Project" htmlFor="t-project" error={errors.projectId?.message}>
          <NativeSelect id="t-project" aria-invalid={!!errors.projectId} {...register('projectId')}><option value="">Choose a project</option>{projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect>
        </Field>
        <Field label="Priority" htmlFor="t-priority">
          <NativeSelect id="t-priority" {...register('priority')}>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}</NativeSelect>
        </Field>
        <Field label="Title" htmlFor="t-title" error={errors.title?.message} className="sm:col-span-2"><Input id="t-title" aria-invalid={!!errors.title} {...register('title')} /></Field>
        <Field label="Description" htmlFor="t-desc" className="sm:col-span-2"><Textarea id="t-desc" rows={5} {...register('description')} /></Field>
        <Field label="Category" htmlFor="t-cat" hint="Leave empty to let automation categorise it">
          <NativeSelect id="t-cat" {...register('categoryId')}><option value="">Automatic</option>{cats.filter((c) => !c.parentId).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect>
        </Field>
        <Field label="Reported by" htmlFor="t-rep"><Input id="t-rep" {...register('reporterName')} /></Field>
        <Field label="Reporter email" htmlFor="t-repe" error={errors.reporterEmail?.message} className="sm:col-span-2"><Input id="t-repe" type="email" {...register('reporterEmail')} /></Field>
      </form>
    </Dialog>
  );
}
