import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound, Plus, Search, Unlock } from 'lucide-react';
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
  const doReset = async () => {
    try { await api.post(`/users/${reset!.id}/reset-password`, { newPassword: pw }); toast.success('Password reset'); setReset(null); setPw(''); } catch (e) { toast.error(errorMessage(e)); }
  };
  const unlock = async (u: UserRow) => {
    try { await api.post(`/users/${u.id}/unlock`); toast.success(`${u.fullName} unlocked`); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card>
      <div className="flex flex-wrap items-center gap-2 border-b p-3">
        <div className="relative w-full sm:w-72"><Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input className="pl-8" placeholder="Search name, code, username or email" aria-label="Search employees" value={q} onChange={(e) => { setPage(0); setQ(e.target.value); }} /></div>
        <NativeSelect aria-label="Role" className="w-40" value={role} onChange={(e) => setRole(e.target.value)}><option value="">All roles</option>{['ADMIN', 'MANAGER', 'TEAM_LEADER', 'L1', 'L2', 'L3'].map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</NativeSelect>
        <div className="flex-1" />
        <Can permission="USER_WRITE"><Button onClick={() => setEditing(null)}><Plus /> Add employee</Button></Can>
      </div>
      {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
      {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No employees found" /> : (
        <>
          <Table>
            <THead><TR><TH>Employee</TH><TH>Role</TH><TH>Shift</TH><TH>Weekly off</TH><TH>Last sign-in</TH><TH>Status</TH><TH /></TR></THead>
            <TBody>
              {data.content.map((u) => (
                <TR key={u.id}>
                  <TD><div className="flex items-center gap-3"><Avatar name={u.fullName} /><div><p className="font-medium">{u.fullName}</p><p className="text-xs text-muted-foreground">{u.employeeCode} · {u.email}</p></div></div></TD>
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
        </>
      ))}
      {editing !== undefined && <UserDialog user={editing} onClose={(c) => { setEditing(undefined); if (c) { invalidateLookups(); reload(); } }} />}
      <Dialog open={!!reset} onOpenChange={(o) => !o && setReset(null)} title={`Reset password for ${reset?.fullName ?? ''}`} description="At least 10 characters with upper and lower case letters and a digit."
        footer={<><Button variant="outline" onClick={() => setReset(null)}>Cancel</Button><Button disabled={pw.length < 10} onClick={doReset}>Reset password</Button></>}>
        <Field label="New password" htmlFor="pw"><Input id="pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
      </Dialog>
    </Card>
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
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {(data ?? []).map((t) => (
            <Card key={t.id}>
              <CardHeader><div><CardTitle>{t.name}</CardTitle><CardDescription>{t.projectName ?? 'No project'} · led by {t.teamLeaderName ?? 'nobody'}</CardDescription></div>
                <Can permission="TEAM_WRITE"><Button variant="ghost" size="sm" onClick={() => open(t)}>Edit</Button></Can></CardHeader>
              <CardContent>
                <div className="mb-3 flex gap-4 text-sm"><span><b className="num">{t.openTickets}</b> open</span><span><b className="num">{t.resolvedLast30Days}</b> resolved in 30 days</span></div>
                <ul className="space-y-1.5">
                  {t.members.map((m) => <li key={m.id} className="flex items-center justify-between text-sm"><span className="flex items-center gap-2"><Avatar name={m.fullName} className="h-6 w-6 text-[9px]" />{m.fullName} <span className="text-xs text-muted-foreground">{m.role}</span></span><span className="num text-xs text-muted-foreground">{m.openTickets} open</span></li>)}
                </ul>
              </CardContent>
            </Card>
          ))}
          {data?.length === 0 && <EmptyState title="No teams yet" />}
        </div>
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
      <CardContent className="px-0">
        {loading && !data ? <TableSkeleton /> : (
          <Table>
            <THead><TR><TH>Engineer</TH><TH className="w-1/3">Open load</TH><TH className="text-right">In progress</TH><TH className="text-right">Pending</TH><TH className="text-right">Breached open</TH><TH className="text-right">Resolved (30d)</TH></TR></THead>
            <TBody>
              {(data ?? []).map((r) => (
                <TR key={r.userId}>
                  <TD><p className="font-medium">{r.fullName}</p><p className="text-xs text-muted-foreground">{r.employeeCode} · {r.role}</p></TD>
                  <TD><div className="flex items-center gap-2"><div className="h-2 flex-1 overflow-hidden rounded-full bg-muted"><div className={`h-full ${r.capacity && r.open >= r.capacity ? 'bg-breach' : 'bg-primary'}`} style={{ width: `${(r.open / max) * 100}%` }} /></div>
                    <span className="num w-14 text-right text-xs">{r.open}{r.capacity ? ` / ${r.capacity}` : ''}</span></div></TD>
                  <TD className="num text-right">{r.inProgress}</TD><TD className="num text-right">{r.pending}</TD>
                  <TD className={`num text-right ${r.breachedOpen ? 'text-breach' : ''}`}>{r.breachedOpen}</TD><TD className="num text-right">{r.resolvedLast30Days}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

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
        <Table>
          <THead><TR><TH>Name</TH><TH>Start</TH><TH>End</TH><TH>Timezone</TH><TH>Status</TH></TR></THead>
          <TBody>{(data ?? []).map((s) => <TR key={s.id}><TD className="font-medium">{s.name}</TD><TD className="num">{s.startTime.slice(0, 5)}</TD><TD className="num">{s.endTime.slice(0, 5)}</TD><TD>{s.timezone}</TD><TD><Badge tone={s.active ? 'green' : 'neutral'}>{s.active ? 'Active' : 'Inactive'}</Badge></TD></TR>)}</TBody>
        </Table>
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
