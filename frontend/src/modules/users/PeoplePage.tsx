import { zodResolver } from '@hookform/resolvers/zod';
import { Clock, KeyRound, Plus, Unlock, UsersRound } from 'lucide-react';
import { motion } from 'motion/react';
import { CardGrid, FilterBar, GridCard, IconTile, SearchField, SegmentedControl, gridItem, gridParent } from '@/components/layout';
import { AvatarStack } from '@/components/viz';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Avatar, Can, EmptyState, ErrorState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { invalidateLookups, useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, errorMessage } from '@/services/api';
import { relative, titleCase } from '@/utils/format';
import type { Page, Shift, Team, UserRow } from '@/types/api';

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export default function PeoplePage() {
  const { can } = useAuth();
  return (
    <>
      <PageHeader title="People & teams" description="Employees, roles, shifts, weekly offs, team membership and current workload." />
      <Tabs defaultValue={can('USER_READ') ? 'people' : 'teams'}>
        <TabsList>
          {can('USER_READ') && <TabsTrigger value="people">Employees</TabsTrigger>}
          {can('TEAM_READ') && <TabsTrigger value="teams">Teams</TabsTrigger>}
          {can('USER_READ') && <TabsTrigger value="workload">Workload</TabsTrigger>}
          <TabsTrigger value="shifts">Shifts</TabsTrigger>
        </TabsList>
        <TabsContent value="people"><Employees /></TabsContent>
        <TabsContent value="teams"><Teams /></TabsContent>
        <TabsContent value="workload"><Workload /></TabsContent>
        <TabsContent value="shifts"><Shifts /></TabsContent>
      </Tabs>
    </>
  );
}

function Employees() {
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi<Page<UserRow>>('/users', { q: q || undefined, role: role || undefined, page, size: 25 });
  const [editing, setEditing] = useState<UserRow | null | undefined>(undefined);
  const [reset, setReset] = useState<UserRow | null>(null);
  const [pw, setPw] = useState('');
  const [layout, setLayout] = useState<'cards' | 'table'>('cards');
  const doReset = async () => {
    try { await api.post(`/users/${reset!.id}/reset-password`, { newPassword: pw }); toast.success('Password reset'); setReset(null); setPw(''); } catch (e) { toast.error(errorMessage(e)); }
  };
  const unlock = async (u: UserRow) => {
    try { await api.post(`/users/${u.id}/unlock`); toast.success(`${u.fullName} unlocked`); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <>
      <FilterBar right={<div className="flex items-center gap-2"><SegmentedControl label="Layout" value={layout} onChange={setLayout} options={[{ value: 'cards', label: 'Cards' }, { value: 'table', label: 'Table' }]} />
        <Can permission="USER_WRITE"><Button onClick={() => setEditing(null)}><Plus /> Add employee</Button></Can></div>}>
        <SearchField label="Search employees" placeholder="Search name, code, username or email…" value={q} onChange={(v) => { setPage(0); setQ(v); }} />
        <NativeSelect aria-label="Role" className="w-40 rounded-full" value={role} onChange={(e) => setRole(e.target.value)}><option value="">All roles</option>{['ADMIN', 'MANAGER', 'TEAM_LEADER', 'L1', 'L2', 'L3'].map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</NativeSelect>
      </FilterBar>
      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? <Card><TableSkeleton /></Card> : data && (data.content.length === 0 ? <Card><EmptyState title="No employees found" /></Card> : layout === 'cards' ? (
        <>
          <CardGrid className="xl:grid-cols-4">
            {data.content.map((u) => (
              <GridCard key={u.id} className="items-center text-center">
                <div className="relative"><Avatar name={u.fullName} className="h-16 w-16 text-lg" />
                  <span className={`absolute bottom-0.5 right-0.5 h-3.5 w-3.5 rounded-full ring-2 ring-card ${u.status === 'ACTIVE' ? 'bg-ok' : u.status === 'LOCKED' ? 'bg-breach' : 'bg-muted-foreground/50'}`} /></div>
                <p className="mt-3 font-display text-[15px] font-semibold">{u.fullName}</p>
                <p className="text-xs text-muted-foreground">{u.jobTitle ?? u.employeeCode}</p>
                <div className="mt-2 flex flex-wrap justify-center gap-1"><Badge tone={u.role === 'ADMIN' ? 'navy' : 'blue'}>{titleCase(u.role)}</Badge>{u.status !== 'ACTIVE' && <Badge tone={u.status === 'LOCKED' ? 'red' : 'neutral'}>{titleCase(u.status)}</Badge>}</div>
                <div className="mt-3 flex w-full gap-1 rounded-xl bg-muted/60 p-1.5">
                  {DAYS.map((d, i) => <span key={d} title={u.weeklyOffs.includes(i + 1) ? `${d}: weekly off` : d} className={`flex-1 rounded-md py-0.5 text-[10px] font-semibold ${u.weeklyOffs.includes(i + 1) ? 'bg-card text-muted-foreground' : 'bg-primary/10 text-primary'}`}>{d[0]}</span>)}
                </div>
                <p className="mt-2 text-xs text-muted-foreground">{u.shiftName ?? 'No shift'} · {u.lastLoginAt ? `seen ${relative(u.lastLoginAt)}` : 'never signed in'}</p>
                <div className="mt-auto flex justify-center pt-2"><Can permission="USER_WRITE">
                  <Button variant="ghost" size="sm" onClick={() => setEditing(u)}>Edit</Button>
                  <Button variant="ghost" size="icon" aria-label={`Reset password for ${u.fullName}`} onClick={() => setReset(u)}><KeyRound /></Button>
                  {u.status === 'LOCKED' && <Button variant="ghost" size="icon" aria-label={`Unlock ${u.fullName}`} onClick={() => unlock(u)}><Unlock /></Button>}
                    </Can></div>
              </GridCard>
            ))}
          </CardGrid>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </>
      ) : (
        <Card className="pt-2">
          <Table>
            <THead><TR><TH>Employee</TH><TH>Role</TH><TH>Shift</TH><TH>Weekly off</TH><TH>Last sign-in</TH><TH>Status</TH><TH /></TR></THead>
            <TBody>
              {data.content.map((u) => (
                <TR key={u.id}>
                  <TD><div className="flex items-center gap-3"><Avatar name={u.fullName} /><div><p className="font-semibold">{u.fullName}</p><p className="text-xs text-muted-foreground">{u.employeeCode} · {u.email}</p></div></div></TD>
                  <TD><Badge tone={u.role === 'ADMIN' ? 'navy' : 'blue'}>{titleCase(u.role)}</Badge><p className="mt-0.5 text-xs text-muted-foreground">{u.jobTitle}</p></TD>
                  <TD className="text-sm">{u.shiftName ?? '—'}</TD>
                  <TD className="text-xs">{u.weeklyOffs.map((d) => DAYS[d - 1]).join(', ') || '—'}</TD>
                  <TD className="text-xs text-muted-foreground">{u.lastLoginAt ? <>{relative(u.lastLoginAt)}<br />{u.lastLoginIp}</> : 'Never'}</TD>
                  <TD><Badge tone={u.status === 'ACTIVE' ? 'green' : u.status === 'LOCKED' ? 'red' : 'neutral'}>{titleCase(u.status)}</Badge></TD>
                  <TD className="text-right whitespace-nowrap">
                    <Can permission="USER_WRITE">
                      <Button variant="ghost" size="sm" onClick={() => setEditing(u)}>Edit</Button>
                      <Button variant="ghost" size="icon" aria-label={`Reset password for ${u.fullName}`} onClick={() => setReset(u)}><KeyRound /></Button>
                      {u.status === 'LOCKED' && <Button variant="ghost" size="icon" aria-label={`Unlock ${u.fullName}`} onClick={() => unlock(u)}><Unlock /></Button>}
                    </Can>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
        </Card>
      ))}
      {editing !== undefined && <UserDialog user={editing} onClose={(c) => { setEditing(undefined); if (c) { invalidateLookups(); reload(); } }} />}
      <Dialog open={!!reset} onOpenChange={(o) => !o && setReset(null)} title={`Reset password for ${reset?.fullName ?? ''}`} description="At least 10 characters with upper and lower case letters and a digit."
        footer={<><Button variant="outline" onClick={() => setReset(null)}>Cancel</Button><Button disabled={pw.length < 10} onClick={doReset}>Reset password</Button></>}>
        <Field label="New password" htmlFor="pw"><Input id="pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
      </Dialog>
    </>
  );
}

const pwRule = z.string().min(10, 'At least 10 characters').regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, 'Needs upper and lower case letters and a digit');
const userSchema = z.object({
  employeeCode: z.string().trim().min(2).max(40).regex(/^[A-Za-z0-9_-]+$/, 'Letters, numbers, - and _ only'),
  username: z.string().trim().min(3).max(80).regex(/^[A-Za-z0-9._-]+$/, 'Letters, numbers, . - _ only'),
  email: z.string().email('Enter a valid email'),
  fullName: z.string().trim().min(2).max(150),
  phone: z.string().max(40).optional(), jobTitle: z.string().max(100).optional(),
  role: z.enum(['ADMIN', 'MANAGER', 'TEAM_LEADER', 'L1', 'L2', 'L3']),
  shiftId: z.string().optional(), timezone: z.string().max(60).optional(),
  maxOpenTickets: z.union([z.literal(''), z.coerce.number().int().min(1).max(500)]).optional(),
  password: z.union([z.literal(''), pwRule]).optional(),
  status: z.enum(['ACTIVE', 'INACTIVE', 'LOCKED']),
  weeklyOffs: z.array(z.string()),
});
type UserForm = z.infer<typeof userSchema>;

function UserDialog({ user, onClose }: { user: UserRow | null; onClose: (c: boolean) => void }) {
  const shifts = useApi<Shift[]>('/shifts');
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<UserForm>({ resolver: zodResolver(userSchema), defaultValues: { role: 'L1', status: 'ACTIVE', weeklyOffs: ['7'] } });
  useEffect(() => {
    if (user) reset({ employeeCode: user.employeeCode, username: user.username, email: user.email, fullName: user.fullName, phone: user.phone ?? '', jobTitle: user.jobTitle ?? '',
      role: user.role, shiftId: user.shiftId ?? '', timezone: user.timezone ?? '', maxOpenTickets: user.maxOpenTickets ?? '', password: '', status: user.status as UserForm['status'],
      weeklyOffs: user.weeklyOffs.map(String) });
  }, [user, reset]);
  const submit = async (v: UserForm) => {
    if (!user && !v.password) { toast.error('Set an initial password'); return; }
    const body = { ...v, shiftId: v.shiftId || undefined, timezone: v.timezone || undefined, maxOpenTickets: v.maxOpenTickets === '' ? undefined : v.maxOpenTickets,
      password: v.password || undefined, phone: v.phone || undefined, jobTitle: v.jobTitle || undefined, weeklyOffs: v.weeklyOffs.map(Number) };
    try {
      if (user) await api.put(`/users/${user.id}`, body); else await api.post('/users', body);
      toast.success(user ? 'Employee updated' : 'Employee added');
      onClose(true);
    } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Dialog open onOpenChange={() => onClose(false)} title={user ? `Edit ${user.fullName}` : 'Add employee'} className="max-w-2xl"
      footer={<><Button variant="outline" onClick={() => onClose(false)}>Cancel</Button><Button loading={isSubmitting} onClick={handleSubmit(submit)}>{user ? 'Save changes' : 'Add employee'}</Button></>}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Full name" htmlFor="u-name" error={errors.fullName?.message}><Input id="u-name" {...register('fullName')} /></Field>
        <Field label="Employee ID" htmlFor="u-code" error={errors.employeeCode?.message}><Input id="u-code" {...register('employeeCode')} /></Field>
        <Field label="Username" htmlFor="u-user" error={errors.username?.message}><Input id="u-user" autoComplete="off" {...register('username')} /></Field>
        <Field label="Email" htmlFor="u-email" error={errors.email?.message}><Input id="u-email" type="email" {...register('email')} /></Field>
        <Field label="Role" htmlFor="u-role"><NativeSelect id="u-role" {...register('role')}>{['ADMIN', 'MANAGER', 'TEAM_LEADER', 'L1', 'L2', 'L3'].map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</NativeSelect></Field>
        <Field label="Job title" htmlFor="u-title"><Input id="u-title" {...register('jobTitle')} /></Field>
        <Field label="Shift" htmlFor="u-shift"><NativeSelect id="u-shift" {...register('shiftId')}><option value="">None</option>{(shifts.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</NativeSelect></Field>
        <Field label="Max open tickets" htmlFor="u-max" error={errors.maxOpenTickets?.message} hint="Used by least-loaded auto-assignment"><Input id="u-max" type="number" {...register('maxOpenTickets')} /></Field>
        <Field label="Phone" htmlFor="u-phone"><Input id="u-phone" {...register('phone')} /></Field>
        <Field label="Status" htmlFor="u-status"><NativeSelect id="u-status" {...register('status')}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="LOCKED">Locked</option></NativeSelect></Field>
        <fieldset className="sm:col-span-2"><legend className="mb-1.5 text-sm font-medium">Weekly offs</legend>
          <div className="flex flex-wrap gap-3">{DAYS.map((d, i) => <label key={d} className="flex items-center gap-1.5 text-sm"><input type="checkbox" value={String(i + 1)} {...register('weeklyOffs')} /> {d}</label>)}</div>
        </fieldset>
        <Field label={user ? 'New password (optional)' : 'Initial password'} htmlFor="u-pw" error={errors.password?.message} className="sm:col-span-2"><Input id="u-pw" type="password" autoComplete="new-password" {...register('password')} /></Field>
      </form>
    </Dialog>
  );
}

function Teams() {
  const lk = useLookups();
  const { data, error, loading, reload } = useApi<Team[]>('/teams');
  const [edit, setEdit] = useState<Team | null | undefined>(undefined);
  const [form, setForm] = useState({ name: '', description: '', projectId: '', teamLeaderId: '', memberIds: [] as string[] });
  const open = (t: Team | null) => {
    setForm(t ? { name: t.name, description: t.description ?? '', projectId: t.projectId ?? '', teamLeaderId: t.teamLeaderId ?? '', memberIds: t.members.map((m) => m.id) }
      : { name: '', description: '', projectId: '', teamLeaderId: '', memberIds: [] });
    setEdit(t);
  };
  const save = async () => {
    const body = { ...form, projectId: form.projectId || undefined, teamLeaderId: form.teamLeaderId || undefined };
    try { if (edit) await api.put(`/teams/${edit.id}`, body); else await api.post('/teams', body); toast.success('Team saved'); setEdit(undefined); invalidateLookups(); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  if (error) return <ErrorState message={error} onRetry={reload} />;
  return (
    <>
      <div className="mb-3 flex justify-end"><Can permission="TEAM_WRITE"><Button onClick={() => open(null)}><Plus /> New team</Button></Can></div>
      {loading && !data ? <TableSkeleton /> : (
        <CardGrid>
          {(data ?? []).map((t) => {
            const top = Math.max(1, ...t.members.map((m) => m.openTickets));
            return (
              <GridCard key={t.id}>
                <div className="flex items-start gap-3">
                  <IconTile icon={UsersRound} tone="brand" />
                  <div className="min-w-0 flex-1"><p className="font-display text-[16px] font-semibold">{t.name}</p><p className="truncate text-xs text-muted-foreground">{t.projectName ?? 'No project'}</p></div>
                  <Can permission="TEAM_WRITE"><Button variant="ghost" size="sm" onClick={() => open(t)}>Edit</Button></Can>
                </div>
                <div className="mt-4 flex items-center gap-3 rounded-xl bg-muted/60 px-3 py-2">
                  {t.teamLeaderName ? <Avatar name={t.teamLeaderName} className="h-8 w-8 text-[10px]" /> : <span className="h-8 w-8 rounded-full bg-muted" />}
                  <div className="min-w-0 flex-1"><p className="text-[11px] text-muted-foreground">Team leader</p><p className="truncate text-sm font-semibold">{t.teamLeaderName ?? 'Not set'}</p></div>
                  <AvatarStack names={t.members.map((m) => m.fullName)} max={4} />
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2 text-center">
                  <div className="rounded-xl bg-primary/10 py-2"><p className="display-num text-xl text-primary">{t.openTickets}</p><p className="text-[11px] text-muted-foreground">open</p></div>
                  <div className="rounded-xl bg-ok/10 py-2"><p className="display-num text-xl text-ok">{t.resolvedLast30Days}</p><p className="text-[11px] text-muted-foreground">resolved in 30 days</p></div>
                </div>
                <ul className="mt-3 space-y-2">
                  {t.members.map((m) => (
                    <li key={m.id} className="flex items-center gap-2 text-sm">
                      <Avatar name={m.fullName} className="h-6 w-6 text-[9px]" /><span className="min-w-0 flex-1 truncate">{m.fullName} <span className="text-xs text-muted-foreground">{m.role}</span></span>
                      <div className="h-1.5 w-14 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-brand" style={{ width: `${(m.openTickets / top) * 100}%` }} /></div>
                      <span className="num w-12 text-right text-xs text-muted-foreground">{m.openTickets} open</span>
                    </li>
                  ))}
                </ul>
              </GridCard>
            );
          })}
          {data?.length === 0 && <Card className="sm:col-span-2 xl:col-span-3"><EmptyState title="No teams yet" /></Card>}
        </CardGrid>
      )}
      <Dialog open={edit !== undefined} onOpenChange={(o) => !o && setEdit(undefined)} title={edit ? `Edit ${edit.name}` : 'New team'}
        footer={<><Button variant="outline" onClick={() => setEdit(undefined)}>Cancel</Button><Button disabled={!form.name.trim()} onClick={save}>Save team</Button></>}>
        <div className="grid gap-3">
          <Field label="Name" htmlFor="tm-name"><Input id="tm-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="Description" htmlFor="tm-desc"><Textarea id="tm-desc" rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
          <Field label="Project" htmlFor="tm-proj"><NativeSelect id="tm-proj" value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value })}><option value="">None</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
          <Field label="Team leader" htmlFor="tm-tl"><NativeSelect id="tm-tl" value={form.teamLeaderId} onChange={(e) => setForm({ ...form, teamLeaderId: e.target.value })}><option value="">None</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect></Field>
          <fieldset><legend className="mb-1.5 text-sm font-medium">Members</legend>
            <div className="grid max-h-48 grid-cols-2 gap-1 overflow-y-auto rounded-md border p-2">
              {lk.users.map((u) => (
                <label key={u.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.memberIds.includes(u.id)}
                  onChange={(e) => setForm({ ...form, memberIds: e.target.checked ? [...form.memberIds, u.id] : form.memberIds.filter((x) => x !== u.id) })} />{u.fullName}</label>
              ))}
            </div>
          </fieldset>
        </div>
      </Dialog>
    </>
  );
}

interface WorkloadRow { userId: string; employeeCode: string; fullName: string; role: string; open: number; inProgress: number; pending: number; resolvedLast30Days: number; breachedOpen: number; capacity?: number }

function Workload() {
  const { data, error, loading, reload } = useApi<WorkloadRow[]>('/users/workload');
  if (error) return <ErrorState message={error} onRetry={reload} />;
  const max = Math.max(1, ...(data ?? []).map((r) => r.capacity ?? r.open));
  return (
    <Card>
      <CardHeader><div><CardTitle>Current workload and productivity</CardTitle><CardDescription>Open tickets against capacity, and tickets resolved in the last 30 days</CardDescription></div></CardHeader>
      <CardContent>
        {loading && !data ? <TableSkeleton /> : (
          <motion.ol className="space-y-2" variants={gridParent} initial="hidden" animate="show">
            {[...(data ?? [])].sort((x, y) => y.resolvedLast30Days - x.resolvedLast30Days).map((r, i) => {
              const over = !!r.capacity && r.open >= r.capacity;
              return (
                <motion.li key={r.userId} variants={gridItem} className="grid items-center gap-3 rounded-2xl bg-muted/50 px-3 py-3 sm:grid-cols-[2rem_minmax(0,14rem)_1fr_auto]">
                  <span className={`hidden h-8 w-8 place-items-center rounded-full font-display text-sm font-bold sm:grid ${i < 3 ? 'bg-brand text-white shadow-glow' : 'bg-card text-muted-foreground'}`}>{i + 1}</span>
                  <div className="flex min-w-0 items-center gap-3"><Avatar name={r.fullName} className="h-9 w-9 text-[11px]" /><div className="min-w-0"><p className="truncate font-semibold">{r.fullName}</p><p className="text-xs text-muted-foreground">{r.employeeCode} · {r.role}</p></div></div>
                  <div>
                    <div className="mb-1 flex justify-between text-xs"><span className="text-muted-foreground">Open load</span><span className={`display-num ${over ? 'text-breach' : ''}`}>{r.open}{r.capacity ? ` / ${r.capacity}` : ''}</span></div>
                    <div className="h-2 overflow-hidden rounded-full bg-card"><motion.div initial={{ width: 0 }} animate={{ width: `${(r.open / max) * 100}%` }} transition={{ duration: 0.6 }} className={`h-full rounded-full ${over ? 'bg-breach' : 'bg-brand'}`} /></div>
                  </div>
                  <div className="flex flex-wrap gap-1.5 text-xs sm:w-[21rem] sm:justify-end">
                    <span className="rounded-full bg-sky/15 px-2 py-1 font-semibold text-sky" title="In progress">{r.inProgress} in progress</span>
                    <span className="rounded-full bg-warn/15 px-2 py-1 font-semibold text-warn" title="Pending">{r.pending} pending</span>
                    {r.breachedOpen > 0 && <span className="rounded-full bg-breach/10 px-2 py-1 font-semibold text-breach">{r.breachedOpen} breached</span>}
                    <span className="rounded-full bg-ok/10 px-2 py-1 font-semibold text-ok" title="Resolved in the last 30 days">{r.resolvedLast30Days} resolved</span>
                  </div>
                </motion.li>
              );
            })}
          </motion.ol>
        )}
      </CardContent>
    </Card>
  );
}

const mins = (t: string) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };

function Shifts() {
  const { data, reload } = useApi<Shift[]>('/shifts');
  const [form, setForm] = useState({ name: '', startTime: '09:00', endTime: '18:00', timezone: 'Asia/Kolkata' });
  const add = async () => {
    try { await api.post('/shifts', form); toast.success('Shift added'); setForm({ ...form, name: '' }); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card>
      <CardHeader><div><CardTitle>Shifts</CardTitle><CardDescription>Working schedules assigned to employees</CardDescription></div></CardHeader>
      <CardContent>
        <div className="space-y-3">
          {(data ?? []).map((s) => {
            const st = mins(s.startTime), en = mins(s.endTime);
            const segs = en > st ? [[st, en]] : [[st, 1440], [0, en]];
            return (
              <div key={s.id} className="rounded-2xl bg-muted/50 p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <IconTile icon={Clock} tone={en > st ? 'sky' : 'ink'} className="h-9 w-9" />
                  <div className="min-w-0 flex-1"><p className="font-semibold">{s.name}</p><p className="text-xs text-muted-foreground">{s.timezone}</p></div>
                  <span className="display-num text-lg">{s.startTime.slice(0, 5)} – {s.endTime.slice(0, 5)}</span>
                  <Badge tone={s.active ? 'green' : 'neutral'}>{s.active ? 'Active' : 'Inactive'}</Badge>
                </div>
                <div className="relative mt-3 h-3 overflow-hidden rounded-full bg-card">
                  {segs.map(([a, b]) => <div key={a} className="absolute inset-y-0 rounded-full bg-brand" style={{ left: `${(a / 1440) * 100}%`, width: `${((b - a) / 1440) * 100}%` }} />)}
                </div>
                <div className="mt-1 flex justify-between text-[10px] text-muted-foreground"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div>
              </div>
            );
          })}
        </div>
        <Can permission="USER_WRITE">
          <div className="mt-4 grid gap-2 border-t pt-4 sm:grid-cols-[1fr_8rem_8rem_12rem_auto] sm:items-end">
            <Field label="Shift name" htmlFor="s-name"><Input id="s-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Start" htmlFor="s-start"><Input id="s-start" type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} /></Field>
            <Field label="End" htmlFor="s-end"><Input id="s-end" type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} /></Field>
            <Field label="Timezone" htmlFor="s-tz"><Input id="s-tz" value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })} /></Field>
            <Button disabled={!form.name.trim()} onClick={add}><Plus /> Add shift</Button>
          </div>
        </Can>
      </CardContent>
    </Card>
  );
}
