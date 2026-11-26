import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Ticket as TicketIcon } from 'lucide-react';
import { CustomFieldInputs, missingRequired, useCustomFields } from './customFields';
import { CardGrid, FilterBar, GridCard, HeroBanner, SearchField, SegmentedControl } from '@/components/layout';
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
import { Avatar, Can, EmptyState, ErrorState, PageHeader, Pagination, PriorityTag, SlaBadge, StatusBadge } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, errorMessage } from '@/services/api';
import { relative } from '@/utils/format';
import type { Category, Page, TicketDetail, TicketSummary } from '@/types/api';

function useTicketCounts() {
  const [c, setC] = useState({ open: 0, unassigned: 0, overdue: 0, breached: 0 });
  useEffect(() => {
    const q = (p: Record<string, unknown>) => api.get<Page<TicketSummary>>('/tickets', { params: { ...p, size: 1 } }).then((r) => r.data.totalElements).catch(() => 0);
    Promise.all([q({ status: OPEN.join(',') }), q({ status: OPEN.join(','), unassigned: true }), q({ overdue: true }), q({ breached: true, status: OPEN.join(',') })])
      .then(([open, unassigned, overdue, breached]) => setC({ open, unassigned, overdue, breached }));
  }, []);
  return c;
}

function SlaBar({ t, compact }: { t: TicketSummary; compact?: boolean }) {
  const due = t.sla.resolutionDueAt ? new Date(t.sla.resolutionDueAt).getTime() : null;
  if (!due) return compact ? null : <p className="mt-3 text-xs text-muted-foreground">No SLA</p>;
  const start = new Date(t.createdAt).getTime();
  const end = t.sla.state === 'MET' || t.sla.state === 'BREACHED' ? Math.min(Date.now(), due + 1) : Date.now();
  const pct = Math.max(2, Math.min(100, ((end - start) / Math.max(1, due - start)) * 100));
  const color = t.sla.state === 'BREACHED' ? 'bg-breach' : t.sla.state === 'AT_RISK' ? 'bg-warn' : t.sla.state === 'MET' ? 'bg-ok' : t.sla.state === 'PAUSED' ? 'bg-muted-foreground/40' : 'bg-brand';
  return (
    <div className={compact ? 'mt-1.5' : 'mt-3'}>
      {!compact && <div className="mb-1 flex justify-between text-[11px] text-muted-foreground"><span>Resolution SLA</span><SlaBadge sla={t.sla} /></div>}
      <div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className={`h-full rounded-full ${color}`} style={{ width: `${pct}%` }} /></div>
    </div>
  );
}

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
  const [layout, setLayout] = useState<'list' | 'cards'>('list');
  const counts = useTicketCounts();
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
      <HeroBanner icon={TicketIcon} title="Service queue" subtitle="Live counts across your projects"
        metrics={[{ label: 'Open', value: counts.open }, { label: 'Unassigned', value: counts.unassigned }, { label: 'Overdue', value: counts.overdue }, { label: 'SLA breached', value: counts.breached }]} />
      <div className="mb-4 flex flex-wrap gap-1.5" role="tablist" aria-label="Ticket views">
        {VIEWS.map((v) => (
          <button key={v.key} role="tab" aria-selected={view === v.key} onClick={() => setSp(v.key === 'open' ? {} : { view: v.key })}
            className={`rounded-full px-4 py-2 text-[13px] font-semibold transition ${view === v.key ? 'bg-brand text-white shadow-glow' : 'bg-card text-muted-foreground shadow-glass hover:text-foreground'}`}>{v.label}</button>
        ))}
      </div>
      <FilterBar right={<SegmentedControl label="Layout" value={layout} onChange={setLayout} options={[{ value: 'list', label: 'List' }, { value: 'cards', label: 'Cards' }]} />}>
        <SearchField label="Search tickets" placeholder="Search number or title…" value={q} onChange={setQ} />
        {lk.projects.length > 0 && <NativeSelect aria-label="Project" className="w-44 rounded-full" value={projectId} onChange={(e) => setProjectId(e.target.value)}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect>}
        <NativeSelect aria-label="Priority" className="w-36 rounded-full" value={priority} onChange={(e) => setPriority(e.target.value)}>
          <option value="">Any priority</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
        </NativeSelect>
        <NativeSelect aria-label="Sort" className="w-40 rounded-full" value={sort} onChange={(e) => setSort(e.target.value)}>
          <option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="due">Due soonest</option><option value="priority">Priority</option><option value="updated">Recently updated</option>
        </NativeSelect>
      </FilterBar>
      {error && <div className="mb-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <Card><TableSkeleton rows={8} cols={6} /></Card> : data && (
        data.content.length === 0 ? <Card><EmptyState title="No tickets in this view" description="Try another view or clear the filters." /></Card> : layout === 'cards' ? (
          <>
            <CardGrid>
              {data.content.map((t) => (
                <GridCard key={t.id} className="overflow-hidden" to={`/tickets/${t.id}`} label={`${t.ticketNumber}: ${t.title}`}>
                  <span className={`absolute inset-x-0 top-0 h-1 ${STRIPE[t.priority]}`} aria-hidden />
                  <div className="flex items-center justify-between gap-2"><span className="num text-xs font-semibold text-muted-foreground">{t.ticketNumber}</span><StatusBadge status={t.status} /></div>
                  <p className="mt-2 font-display text-[15px] font-semibold leading-snug">{t.title}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{t.projectName} · {t.clientName}</p>
                  <SlaBar t={t} />
                  <div className="mt-auto flex items-center justify-between pt-4">
                    <span className="flex items-center gap-2 text-sm">{t.assigneeName ? <><Avatar name={t.assigneeName} className="h-7 w-7 text-[9px]" />{t.assigneeName}</> : <span className="text-muted-foreground">Unassigned</span>}</span>
                    <PriorityTag priority={t.priority} />
                  </div>
                </GridCard>
              ))}
            </CardGrid>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ) : (
          <Card className="pt-2">
            <Table>
              <THead><TR><TH>Ticket</TH><TH>Priority</TH><TH>Status</TH><TH className="min-w-[180px]">SLA</TH><TH>Assignee</TH><TH>Project</TH><TH>Age</TH></TR></THead>
              <TBody>
                {data.content.map((t) => (
                  <TR key={t.id}>
                    <TD className="relative max-w-[28rem] pl-5">
                      <span className={`absolute inset-y-2 left-1.5 w-1 rounded-full ${STRIPE[t.priority]}`} aria-hidden />
                      <Link to={`/tickets/${t.id}`} className="font-semibold hover:text-primary">{t.title}</Link>
                      <p className="num text-xs text-muted-foreground">{t.ticketNumber}{t.categoryName ? ` · ${t.categoryName}` : ''}{t.escalationLevel > 0 ? ` · escalated L${t.escalationLevel}` : ''}</p>
                    </TD>
                    <TD><PriorityTag priority={t.priority} /></TD>
                    <TD><StatusBadge status={t.status} /></TD>
                    <TD><SlaBadge sla={t.sla} /><SlaBar t={t} compact /></TD>
                    <TD className="text-sm">{t.assigneeName ? <span className="flex items-center gap-2"><Avatar name={t.assigneeName} className="h-7 w-7 text-[9px]" />{t.assigneeName}</span> : <span className="text-muted-foreground">Unassigned</span>}</TD>
                    <TD className="text-xs text-muted-foreground">{t.projectName}<br />{t.clientName}</TD>
                    <TD className="num whitespace-nowrap text-xs text-muted-foreground" title={t.createdAt}>{relative(t.createdAt)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </Card>
        ))}
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
  const fieldDefs = useCustomFields(projectId);
  const [cf, setCf] = useState<Record<string, string>>({});
  const [cfTried, setCfTried] = useState(false);
  useEffect(() => { setCf({}); setCfTried(false); }, [projectId]);
  useEffect(() => {
    if (!projectId) { setCats([]); return; }
    api.get<Category[]>(`/projects/${projectId}/categories`).then((r) => setCats(r.data)).catch(() => setCats([]));
  }, [projectId]);
  const submit = async (v: Form) => {
    setCfTried(true);
    const missing = missingRequired(fieldDefs, cf);
    if (missing.length) { toast.error(`${missing.join(', ')} ${missing.length > 1 ? 'are' : 'is'} required`); return; }
    const customFields = Object.fromEntries(Object.entries(cf).filter(([, x]) => x !== ''));
    try {
      const r = await api.post<TicketDetail>('/tickets', { ...v, categoryId: v.categoryId || undefined, reporterEmail: v.reporterEmail || undefined, customFields });
      toast.success(`Ticket ${r.data.summary.ticketNumber} created`);
      reset();
      setCf({});
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
        <CustomFieldInputs defs={fieldDefs} values={cf} onChange={setCf} showErrors={cfTried} />
      </form>
    </Dialog>
  );
}
