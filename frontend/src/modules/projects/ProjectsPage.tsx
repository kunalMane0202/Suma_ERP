import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2, CircleAlert, Plus } from 'lucide-react';
import { CardGrid, FilterBar, GridCard, SearchField, SegmentedControl } from '@/components/layout';
import { AvatarStack } from '@/components/viz';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Tooltip } from '@/components/ui/tooltip';
import { Can, EmptyState, ErrorState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { invalidateLookups, useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import type { Page, Project } from '@/types/api';

export default function ProjectsPage() {
  const [sp] = useSearchParams();
  const lk = useLookups();
  const [q, setQ] = useState('');
  const [clientId, setClientId] = useState(sp.get('clientId') ?? '');
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi<Page<Project>>('/projects', { q: q || undefined, clientId: clientId || undefined, page, size: 20 });
  const [editing, setEditing] = useState<Project | null | undefined>(undefined);
  const [layout, setLayout] = useState<'cards' | 'table'>('cards');
  const navigate = useNavigate();
  return (
    <>
      <PageHeader title="Projects" description="Support engagements per client: leadership, Project Lead report recipient, support hours and staffing."
        actions={<Can permission="PROJECT_WRITE"><Button onClick={() => setEditing(null)}><Plus /> New project</Button></Can>} />
      <FilterBar right={<SegmentedControl label="Layout" value={layout} onChange={setLayout} options={[{ value: 'cards', label: 'Cards' }, { value: 'table', label: 'Table' }]} />}>
        <SearchField label="Search projects" placeholder="Search name or code" value={q} onChange={(v) => { setPage(0); setQ(v); }} />
        {lk.clients.length > 0 && <NativeSelect aria-label="Client" className="w-52 rounded-full" value={clientId} onChange={(e) => setClientId(e.target.value)}><option value="">All clients</option>{lk.clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect>}
      </FilterBar>
      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? <Card><TableSkeleton /></Card> : data && (data.content.length === 0 ? <Card><EmptyState title="No projects found" /></Card> : layout === 'cards' ? (
        <>
          <CardGrid>
            {data.content.map((p) => (
              <GridCard key={p.id} onClick={() => navigate(`/projects/${p.id}`)} className="relative overflow-hidden">
                <div className="pointer-events-none absolute -right-10 -top-10 h-28 w-28 rounded-full bg-violet/10 blur-xl" />
                <div className="relative flex items-start justify-between gap-2">
                  <div className="min-w-0"><p className="text-xs font-semibold text-primary">{p.code}</p><Link to={`/projects/${p.id}`} onClick={(e) => e.stopPropagation()} className="mt-0.5 block font-display text-[16px] font-semibold leading-snug hover:text-primary">{p.name}</Link>
                    <p className="text-xs text-muted-foreground">{p.clientName}</p></div>
                  <Badge tone={p.status === 'ACTIVE' ? 'green' : 'neutral'}>{p.status === 'ACTIVE' ? 'Active' : 'Inactive'}</Badge>
                </div>
                <div className="relative mt-4 flex items-center justify-between">
                  <AvatarStack names={[p.managerName, p.teamLeaderName, p.projectLeadName].filter((x): x is string => !!x)} />
                  <div className="text-right"><p className="display-num text-xl">{p.memberCount}</p><p className="text-[11px] text-muted-foreground">people</p></div>
                </div>
                <div className="relative mt-4">
                  <div className="mb-1 flex justify-between text-xs"><span className="text-muted-foreground">Open tickets</span><span className="display-num">{p.openTickets} / {p.totalTickets}</span></div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-brand" style={{ width: `${p.totalTickets ? Math.max(4, (p.openTickets / p.totalTickets) * 100) : 0}%` }} /></div>
                </div>
                <div className="relative mt-4 flex flex-wrap items-center gap-2 border-t border-border/60 pt-3 text-xs">
                  {p.projectLeadEmail ? (
                    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 font-semibold ${p.projectLeadEmailVerified ? 'bg-ok/10 text-ok' : 'bg-warn/15 text-warn'}`}>
                      {p.projectLeadEmailVerified ? <CheckCircle2 className="h-3 w-3" /> : <CircleAlert className="h-3 w-3" />}{p.projectLeadEmailVerified ? 'Report recipient verified' : 'Recipient not verified'}</span>
                  ) : <span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">No report recipient</span>}
                  {p.supportHours && <span className="truncate text-muted-foreground">{p.supportHours}</span>}
                </div>
              </GridCard>
            ))}
          </CardGrid>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </>
      ) : (
        <Card className="pt-2">
          <Table>
            <THead><TR><TH>Project</TH><TH>Client</TH><TH>Manager</TH><TH>Team leader</TH><TH>Project Lead</TH><TH className="text-right">People</TH><TH className="text-right">Open tickets</TH><TH>Status</TH></TR></THead>
            <TBody>
              {data.content.map((p) => (
                <TR key={p.id}>
                  <TD><Link to={`/projects/${p.id}`} className="font-semibold hover:text-primary">{p.name}</Link><p className="text-xs text-muted-foreground">{p.code}</p></TD>
                  <TD>{p.clientName}</TD><TD>{p.managerName ?? '—'}</TD><TD>{p.teamLeaderName ?? '—'}</TD>
                  <TD>{p.projectLeadName ?? '—'}{p.projectLeadEmail && <Tooltip content={p.projectLeadEmailVerified ? 'Verified: monthly reports are delivered' : 'Not verified: monthly reports will not be sent'}>
                    <span className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">{p.projectLeadEmailVerified ? <CheckCircle2 className="h-3 w-3 text-ok" /> : <CircleAlert className="h-3 w-3 text-warn" />}{p.projectLeadEmail}</span></Tooltip>}</TD>
                  <TD className="num text-right">{p.memberCount}</TD><TD className="num text-right">{p.openTickets}</TD>
                  <TD><Badge tone={p.status === 'ACTIVE' ? 'green' : 'neutral'}>{p.status === 'ACTIVE' ? 'Active' : 'Inactive'}</Badge></TD>
                </TR>
              ))}
            </TBody>
          </Table>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </Card>
      ))}
      {editing !== undefined && <ProjectDialog project={editing} onClose={(c) => { setEditing(undefined); if (c) { invalidateLookups(); reload(); } }} />}
    </>
  );
}

const schema = z.object({
  clientId: z.string().uuid('Choose a client'),
  code: z.string().trim().min(2).max(30).regex(/^[A-Za-z0-9_-]+$/, 'Letters, numbers, - and _ only'),
  name: z.string().trim().min(2).max(200),
  description: z.string().max(4000).optional(),
  managerId: z.string().optional(), teamLeaderId: z.string().optional(), projectLeadId: z.string().optional(),
  projectLeadEmail: z.union([z.literal(''), z.string().email('Enter a valid email')]).optional(),
  supportHours: z.string().max(100).optional(), timezone: z.string().max(60).optional(),
  status: z.enum(['ACTIVE', 'INACTIVE']), startDate: z.string().optional(), endDate: z.string().optional(),
});
type Form = z.infer<typeof schema>;

export function ProjectDialog({ project, onClose }: { project: Project | null; onClose: (changed: boolean) => void }) {
  const lk = useLookups();
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { status: 'ACTIVE', timezone: 'Asia/Kolkata' } });
  useEffect(() => {
    if (project) reset({ clientId: project.clientId, code: project.code, name: project.name, description: project.description ?? '', managerId: project.managerId ?? '',
      teamLeaderId: project.teamLeaderId ?? '', projectLeadId: project.projectLeadId ?? '', projectLeadEmail: project.projectLeadEmail ?? '', supportHours: project.supportHours ?? '',
      timezone: project.timezone, status: project.status, startDate: project.startDate ?? '', endDate: project.endDate ?? '' });
  }, [project, reset]);
  const submit = async (v: Form) => {
    const body = Object.fromEntries(Object.entries(v).map(([k, x]) => [k, x === '' ? undefined : x]));
    try {
      if (project) await api.put(`/projects/${project.id}`, body); else await api.post('/projects', body);
      toast.success(project ? 'Project updated' : 'Project created');
      onClose(true);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  const people = (roles: string[]) => lk.users.filter((u) => roles.includes(u.role));
  return (
    <Dialog open onOpenChange={() => onClose(false)} title={project ? `Edit ${project.name}` : 'New project'} className="max-w-2xl"
      footer={<><Button variant="outline" onClick={() => onClose(false)}>Cancel</Button><Button loading={isSubmitting} onClick={handleSubmit(submit)}>{project ? 'Save changes' : 'Create project'}</Button></>}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Client" htmlFor="p-client" error={errors.clientId?.message}><NativeSelect id="p-client" {...register('clientId')}><option value="">Choose</option>{lk.clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect></Field>
        <Field label="Code" htmlFor="p-code" error={errors.code?.message}><Input id="p-code" {...register('code')} /></Field>
        <Field label="Name" htmlFor="p-name" error={errors.name?.message} className="sm:col-span-2"><Input id="p-name" {...register('name')} /></Field>
        <Field label="Description" htmlFor="p-desc" className="sm:col-span-2"><Textarea id="p-desc" rows={2} {...register('description')} /></Field>
        <Field label="Project manager" htmlFor="p-mgr"><NativeSelect id="p-mgr" {...register('managerId')}><option value="">None</option>{people(['ADMIN', 'MANAGER']).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect></Field>
        <Field label="Team leader" htmlFor="p-tl"><NativeSelect id="p-tl" {...register('teamLeaderId')}><option value="">None</option>{people(['TEAM_LEADER', 'MANAGER', 'L3']).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect></Field>
        <Field label="Project Lead" htmlFor="p-lead" hint="Receives the monthly attendance report"><NativeSelect id="p-lead" {...register('projectLeadId')}><option value="">None</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.role})</option>)}</NativeSelect></Field>
        <Field label="Project Lead report email" htmlFor="p-leademail" error={errors.projectLeadEmail?.message} hint="Verified automatically only when it matches the lead's account email"><Input id="p-leademail" type="email" {...register('projectLeadEmail')} /></Field>
        <Field label="Support hours" htmlFor="p-hours"><Input id="p-hours" placeholder="Mon-Fri 09:00-18:00" {...register('supportHours')} /></Field>
        <Field label="Timezone" htmlFor="p-tz"><Input id="p-tz" {...register('timezone')} /></Field>
        <Field label="Start date" htmlFor="p-start"><Input id="p-start" type="date" {...register('startDate')} /></Field>
        <Field label="End date" htmlFor="p-end"><Input id="p-end" type="date" {...register('endDate')} /></Field>
        <Field label="Status" htmlFor="p-status"><NativeSelect id="p-status" {...register('status')}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></NativeSelect></Field>
      </form>
    </Dialog>
  );
}
