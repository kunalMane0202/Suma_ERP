import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, RotateCw, Trash2 } from 'lucide-react';
import { Fragment, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { EmptyState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, errorMessage } from '@/services/api';
import { dateTime, titleCase } from '@/utils/format';
import type { AttendanceSettings } from '@/types/attendance';
import type { Page } from '@/types/api';

export default function SettingsPage() {
  const { can } = useAuth();
  const first = can('ATTENDANCE_MANAGE') ? 'attendance' : can('SETTINGS_MANAGE') ? 'system' : 'roles';
  return (
    <>
      <PageHeader title="Configuration" description="Attendance policies, platform settings, roles and permissions. Every change is recorded in the audit trail." />
      <Tabs defaultValue={first}>
        <TabsList>
          {can('ATTENDANCE_MANAGE') && <TabsTrigger value="attendance">Attendance</TabsTrigger>}
          {can('ATTENDANCE_MANAGE') && <TabsTrigger value="breaks">Break policies</TabsTrigger>}
          {can('SETTINGS_MANAGE') && <TabsTrigger value="system">System</TabsTrigger>}
          {can('SETTINGS_MANAGE') && <TabsTrigger value="templates">Email templates</TabsTrigger>}
          {can('ROLE_MANAGE') && <TabsTrigger value="roles">Roles & permissions</TabsTrigger>}
          {can('SETTINGS_MANAGE') && <TabsTrigger value="jobs">Scheduled jobs</TabsTrigger>}
          {can('NOTIFICATION_MANAGE') && <TabsTrigger value="email">Email delivery</TabsTrigger>}
        </TabsList>
        <TabsContent value="attendance"><AttendanceSettingsTab /></TabsContent>
        <TabsContent value="breaks"><BreakPolicies /></TabsContent>
        <TabsContent value="system"><SystemSettings /></TabsContent>
        <TabsContent value="templates"><Templates /></TabsContent>
        <TabsContent value="roles"><Roles /></TabsContent>
        <TabsContent value="jobs"><Jobs /></TabsContent>
        <TabsContent value="email"><EmailLog /></TabsContent>
      </Tabs>
    </>
  );
}

const opt = z.preprocess((v) => (v === '' || v == null || Number.isNaN(v) ? undefined : Number(v)), z.number().int().positive().optional());
const req = (min: number, max: number) => z.coerce.number().int().min(min).max(max);
const settingsSchema = z.object({
  defaultWorkHours: z.preprocess((v) => (v === '' || v == null ? undefined : Number(v)), z.number().min(0.5).max(24).optional()),
  idleThresholdMinutes: req(1, 480), heartbeatTimeoutMinutes: req(2, 1440), missingLogoutAfterHours: req(1, 72),
  maxBreakMinutes: opt, maxDailyBreakMinutes: opt, maxBreaksPerDay: opt, breakReminderIntervalMinutes: opt,
  reportingTimezone: z.string().min(1), notifyTeamLeaderOnIdle: z.boolean(), monthlyReportEnabled: z.boolean(),
  monthlyReportHour: req(0, 23), emailMaxAttempts: req(1, 20), emailRetryBackoffMinutes: req(1, 1440), retentionDays: req(30, 3650),
});
type SettingsForm = z.infer<typeof settingsSchema>;

function AttendanceSettingsTab() {
  const lk = useLookups();
  const [scope, setScope] = useState('');
  const all = useApi<AttendanceSettings[]>('/attendance/settings');
  const current = all.data?.find((s) => (scope ? s.projectId === scope : !s.projectId)) ?? all.data?.find((s) => !s.projectId);
  const isOverride = !!scope && !!all.data?.find((s) => s.projectId === scope);
  const { register, handleSubmit, reset, watch, setValue, formState: { errors, isSubmitting } } = useForm<SettingsForm>({ resolver: zodResolver(settingsSchema) });
  useEffect(() => { if (current) reset({ ...current, defaultWorkHours: current.defaultWorkHours ?? undefined } as SettingsForm); }, [current, reset]);
  const submit = async (v: SettingsForm) => {
    try {
      await api.put(scope ? `/attendance/settings/projects/${scope}` : '/attendance/settings', v);
      toast.success(scope ? 'Project override saved' : 'Organisation attendance settings saved');
      all.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  if (!all.data) return <TableSkeleton />;
  const num = (name: keyof SettingsForm, label: string, hint?: string, placeholder?: string) => (
    <Field label={label} htmlFor={`as-${name}`} error={errors[name]?.message as string | undefined} hint={hint}>
      <Input id={`as-${name}`} type="number" placeholder={placeholder} {...register(name)} />
    </Field>
  );
  return (
    <Card>
      <CardHeader className="flex-wrap">
        <div><CardTitle>Attendance & monitoring policy</CardTitle><CardDescription>Break limits are not preset: leave them empty if your company has no limit. A project override replaces the organisation default for that project.</CardDescription></div>
        <div className="flex items-center gap-2">
          <NativeSelect aria-label="Scope" className="w-56" value={scope} onChange={(e) => setScope(e.target.value)}>
            <option value="">Organisation default</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}{all.data?.some((s) => s.projectId === p.id) ? ' (override)' : ''}</option>)}
          </NativeSelect>
          {isOverride && <Button variant="outline" size="sm" onClick={async () => { await api.delete(`/attendance/settings/projects/${scope}`); toast.success('Override removed'); all.reload(); }}>Remove override</Button>}
        </div>
      </CardHeader>
      <CardContent>
        {scope && !isOverride && <p className="mb-4 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">This project uses the organisation default. Saving creates a project-specific override.</p>}
        <form onSubmit={handleSubmit(submit)} noValidate className="space-y-6">
          <section><h3 className="mb-3 text-sm font-semibold">Working time</h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {num('defaultWorkHours', 'Default work hours per day', 'Days below this are flagged as short', 'Not set')}
              <Field label="Reporting timezone" htmlFor="as-tz" hint="Day boundaries and monthly report schedule"><Input id="as-tz" {...register('reportingTimezone')} /></Field>
              {num('missingLogoutAfterHours', 'Close sessions open longer than (hours)')}
              {num('heartbeatTimeoutMinutes', 'Disconnect after no heartbeat for (minutes)', 'Used to detect closed browsers')}
            </div></section>
          <section><h3 className="mb-3 text-sm font-semibold">Breaks</h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {num('maxBreakMinutes', 'Maximum minutes per break', undefined, 'No limit')}
              {num('maxDailyBreakMinutes', 'Maximum break minutes per day', undefined, 'No limit')}
              {num('maxBreaksPerDay', 'Maximum breaks per day', undefined, 'No limit')}
              {num('breakReminderIntervalMinutes', 'Remind every (minutes) during a break', undefined, 'No reminders')}
            </div></section>
          <section><h3 className="mb-3 text-sm font-semibold">Inactivity</h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {num('idleThresholdMinutes', 'Ask for status after inactivity of (minutes)', 'Default 20. In-app activity only.')}
              <label className="flex items-center gap-2 self-end pb-2 text-sm"><Switch checked={!!watch('notifyTeamLeaderOnIdle')} onCheckedChange={(v) => setValue('notifyTeamLeaderOnIdle', v)} /> Notify team leader when someone is inactive</label>
            </div></section>
          <section><h3 className="mb-3 text-sm font-semibold">Monthly Project Lead report</h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <label className="flex items-center gap-2 self-end pb-2 text-sm"><Switch checked={!!watch('monthlyReportEnabled')} onCheckedChange={(v) => setValue('monthlyReportEnabled', v)} /> Send automatically on the 1st</label>
              {num('monthlyReportHour', 'Send at hour (0–23, reporting timezone)')}
              {num('emailMaxAttempts', 'Delivery attempts')}
              {num('emailRetryBackoffMinutes', 'First retry after (minutes)', 'Doubles after each failure')}
              {num('retentionDays', 'Keep attendance history for (days)')}
            </div></section>
          <div className="flex items-center justify-between border-t pt-4">
            <p className="text-xs text-muted-foreground">{current?.updatedAt ? `Last changed ${dateTime(current.updatedAt)}` : ''}</p>
            <Button type="submit" loading={isSubmitting}>Save attendance settings</Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

interface Policy { id: string; name: string; projectId?: string; breakCategory?: string; maxDurationMinutes?: number; maxPerDay?: number; reminderAfterMinutes?: number; active: boolean }

function BreakPolicies() {
  const lk = useLookups();
  const { data, reload } = useApi<Policy[]>('/attendance/policies');
  const cats = useApi<{ code: string; label: string }[]>('/attendance/categories');
  const [edit, setEdit] = useState<Partial<Policy> | null>(null);
  const save = async () => {
    try { if (edit?.id) await api.put(`/attendance/policies/${edit.id}`, edit); else await api.post('/attendance/policies', edit); toast.success('Break policy saved'); setEdit(null); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card>
      <CardHeader><div><CardTitle>Break policies by type</CardTitle><CardDescription>Optional limits for a break type, globally or per project. The most specific matching policy wins.</CardDescription></div>
        <Button size="sm" onClick={() => setEdit({ name: '', active: true })}><Plus /> Add policy</Button></CardHeader>
      <CardContent className="px-0">
        {(data ?? []).length === 0 ? <EmptyState title="No break policies" description="Only the general limits from attendance settings apply." /> : (
          <Table>
            <THead><TR><TH>Policy</TH><TH>Project</TH><TH>Break type</TH><TH>Max minutes</TH><TH>Max per day</TH><TH>Reminder after</TH><TH>Active</TH><TH /></TR></THead>
            <TBody>{data!.map((p) => (
              <TR key={p.id}><TD className="font-medium">{p.name}</TD><TD>{lk.projects.find((x) => x.id === p.projectId)?.name ?? 'All'}</TD><TD>{p.breakCategory ? titleCase(p.breakCategory) : 'Any'}</TD>
                <TD className="num">{p.maxDurationMinutes ?? '—'}</TD><TD className="num">{p.maxPerDay ?? '—'}</TD><TD className="num">{p.reminderAfterMinutes ?? '—'}</TD><TD>{p.active ? 'Yes' : 'No'}</TD>
                <TD className="whitespace-nowrap text-right"><Button variant="ghost" size="sm" onClick={() => setEdit(p)}>Edit</Button><Button variant="ghost" size="icon" aria-label="Delete policy" onClick={async () => { await api.delete(`/attendance/policies/${p.id}`); reload(); }}><Trash2 /></Button></TD></TR>
            ))}</TBody>
          </Table>
        )}
      </CardContent>
      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)} title={edit?.id ? 'Edit break policy' : 'Add break policy'}
        footer={<><Button variant="outline" onClick={() => setEdit(null)}>Cancel</Button><Button disabled={!edit?.name} onClick={save}>Save</Button></>}>
        {edit && <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name" htmlFor="bp-name" className="sm:col-span-2"><Input id="bp-name" value={edit.name ?? ''} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
          <Field label="Project" htmlFor="bp-proj"><NativeSelect id="bp-proj" value={edit.projectId ?? ''} onChange={(e) => setEdit({ ...edit, projectId: e.target.value || undefined })}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
          <Field label="Break type" htmlFor="bp-cat"><NativeSelect id="bp-cat" value={edit.breakCategory ?? ''} onChange={(e) => setEdit({ ...edit, breakCategory: e.target.value || undefined })}><option value="">Any type</option>{(cats.data ?? []).map((c) => <option key={c.code} value={c.code}>{c.label}</option>)}</NativeSelect></Field>
          <Field label="Max minutes per break" htmlFor="bp-max"><Input id="bp-max" type="number" min={1} value={edit.maxDurationMinutes ?? ''} onChange={(e) => setEdit({ ...edit, maxDurationMinutes: e.target.value ? Number(e.target.value) : undefined })} /></Field>
          <Field label="Max per day" htmlFor="bp-day"><Input id="bp-day" type="number" min={1} value={edit.maxPerDay ?? ''} onChange={(e) => setEdit({ ...edit, maxPerDay: e.target.value ? Number(e.target.value) : undefined })} /></Field>
          <Field label="Remind after (minutes)" htmlFor="bp-rem"><Input id="bp-rem" type="number" min={1} value={edit.reminderAfterMinutes ?? ''} onChange={(e) => setEdit({ ...edit, reminderAfterMinutes: e.target.value ? Number(e.target.value) : undefined })} /></Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm"><Switch checked={!!edit.active} onCheckedChange={(v) => setEdit({ ...edit, active: v })} /> Active</label>
        </div>}
      </Dialog>
    </Card>
  );
}

interface Setting { key: string; value?: string; valueType: string; category: string; description?: string; updatedAt: string }
interface Ref { statuses: { code: string; label: string; color: string }[]; priorities: { code: string; label: string; color: string }[] }

function SystemSettings() {
  const { data, reload } = useApi<Setting[]>('/settings');
  const ref = useApi<Ref>('/settings/reference');
  const [values, setValues] = useState<Record<string, string>>({});
  useEffect(() => { if (data) setValues(Object.fromEntries(data.map((s) => [s.key, s.value ?? '']))); }, [data]);
  const save = async (key: string) => {
    try { await api.put(`/settings/${key}`, { value: values[key] }); toast.success('Setting saved'); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const groups = [...new Set((data ?? []).map((s) => s.category))];
  return (
    <div className="grid gap-4 xl:grid-cols-3">
      <div className="space-y-4 xl:col-span-2">
        {groups.map((g) => (
          <Card key={g}>
            <CardHeader><CardTitle>{titleCase(g)}</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {data!.filter((s) => s.category === g).map((s) => (
                <div key={s.key} className="grid items-end gap-2 sm:grid-cols-[1fr_14rem_auto]">
                  <div><p className="text-sm font-medium">{s.description}</p><p className="font-mono text-xs text-muted-foreground">{s.key}</p></div>
                  <Input aria-label={s.description ?? s.key} type={s.valueType === 'NUMBER' ? 'number' : 'text'} value={values[s.key] ?? ''} onChange={(e) => setValues({ ...values, [s.key]: e.target.value })} />
                  <Button size="sm" variant="outline" disabled={values[s.key] === (s.value ?? '')} onClick={() => save(s.key)}>Save</Button>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
      <div className="space-y-4">
        {ref.data && (['statuses', 'priorities'] as const).map((k) => (
          <Card key={k}>
            <CardHeader><div><CardTitle>Ticket {k}</CardTitle><CardDescription>Display labels and colours. The lifecycle itself is fixed.</CardDescription></div></CardHeader>
            <CardContent className="space-y-2">
              {ref.data![k].map((s) => <ConfigRow key={s.code} kind={k} item={s} onSaved={ref.reload} />)}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function ConfigRow({ kind, item, onSaved }: { kind: 'statuses' | 'priorities'; item: { code: string; label: string; color: string }; onSaved: () => void }) {
  const [label, setLabel] = useState(item.label);
  const [color, setColor] = useState(item.color);
  const dirty = label !== item.label || color !== item.color;
  return (
    <div className="flex items-center gap-2">
      <input type="color" aria-label={`${item.code} colour`} value={color} onChange={(e) => setColor(e.target.value)} className="h-8 w-8 cursor-pointer rounded border bg-transparent" />
      <Input aria-label={`${item.code} label`} className="h-8" value={label} onChange={(e) => setLabel(e.target.value)} />
      <Button size="sm" variant="ghost" disabled={!dirty} onClick={async () => { try { await api.put(`/settings/${kind}/${item.code}`, { label, color }); toast.success('Saved'); onSaved(); } catch (e) { toast.error(errorMessage(e)); } }}>Save</Button>
    </div>
  );
}

function Templates() {
  const { data, reload } = useApi<{ code: string; subject: string; body: string; description?: string }[]>('/settings/email-templates');
  const [edit, setEdit] = useState<{ code: string; subject: string; body: string } | null>(null);
  return (
    <Card>
      <CardContent className="px-0 pt-2">
        <Table><THead><TR><TH>Template</TH><TH>Subject</TH><TH /></TR></THead>
          <TBody>{(data ?? []).map((t) => <TR key={t.code}><TD><p className="font-medium">{t.description}</p><p className="font-mono text-xs text-muted-foreground">{t.code}</p></TD><TD className="text-sm">{t.subject}</TD>
            <TD className="text-right"><Button variant="ghost" size="sm" onClick={() => setEdit({ code: t.code, subject: t.subject, body: t.body.replace(/\\n/g, '\n') })}>Edit</Button></TD></TR>)}</TBody></Table>
      </CardContent>
      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)} title="Edit email template" description="Placeholders like {{name}} are replaced when the email is sent." className="max-w-2xl"
        footer={<><Button variant="outline" onClick={() => setEdit(null)}>Cancel</Button><Button onClick={async () => { try { await api.put(`/settings/email-templates/${edit!.code}`, { subject: edit!.subject, body: edit!.body }); toast.success('Template saved'); setEdit(null); reload(); } catch (e) { toast.error(errorMessage(e)); } }}>Save template</Button></>}>
        {edit && <div className="space-y-3"><Field label="Subject" htmlFor="tpl-s"><Input id="tpl-s" value={edit.subject} onChange={(e) => setEdit({ ...edit, subject: e.target.value })} /></Field>
          <Field label="Body" htmlFor="tpl-b"><Textarea id="tpl-b" rows={10} className="font-mono text-xs" value={edit.body} onChange={(e) => setEdit({ ...edit, body: e.target.value })} /></Field></div>}
      </Dialog>
    </Card>
  );
}

interface Role { code: string; name: string; description?: string; permissions: string[] }
interface Perm { code: string; module: string; description?: string }

function Roles() {
  const roles = useApi<Role[]>('/roles');
  const perms = useApi<Perm[]>('/permissions');
  const [draft, setDraft] = useState<Record<string, Set<string>>>({});
  useEffect(() => { if (roles.data) setDraft(Object.fromEntries(roles.data.map((r) => [r.code, new Set(r.permissions)]))); }, [roles.data]);
  if (!roles.data || !perms.data) return <TableSkeleton />;
  const modules = [...new Set(perms.data.map((p) => p.module))];
  const toggle = (role: string, perm: string) => setDraft((d) => { const s = new Set(d[role]); if (s.has(perm)) s.delete(perm); else s.add(perm); return { ...d, [role]: s }; });
  const dirty = (r: Role) => { const s = draft[r.code]; return !!s && (s.size !== r.permissions.length || r.permissions.some((p) => !s.has(p))); };
  const save = async (r: Role) => {
    try { await api.put(`/roles/${r.code}/permissions`, { permissions: [...draft[r.code]] }); toast.success(`${r.name} permissions saved. Takes effect within a minute.`); roles.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card>
      <CardHeader><div><CardTitle>Role permission matrix</CardTitle><CardDescription>Enforced on the server for every request. Project Lead is a per-project designation, not a role.</CardDescription></div></CardHeader>
      <CardContent className="px-0">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-card"><tr className="border-b"><th className="px-4 py-2 text-left text-xs font-medium text-muted-foreground">Permission</th>
              {roles.data.map((r) => <th key={r.code} className="px-2 py-2 text-center text-xs font-medium">{r.name}{dirty(r) && <Button size="sm" className="mt-1 h-6 w-full text-[11px]" onClick={() => save(r)}>Save</Button>}</th>)}</tr></thead>
            <tbody>
              {modules.map((m) => (
                <Fragment key={m}>
                  <tr className="bg-muted/50"><td colSpan={roles.data!.length + 1} className="px-4 py-1.5 text-xs font-semibold">{titleCase(m)}</td></tr>
                  {perms.data!.filter((p) => p.module === m).map((p) => (
                    <tr key={p.code} className="border-b">
                      <td className="px-4 py-1.5"><p>{p.description}</p><p className="font-mono text-[11px] text-muted-foreground">{p.code}</p></td>
                      {roles.data!.map((r) => <td key={r.code} className="text-center"><input type="checkbox" aria-label={`${r.name}: ${p.code}`} checked={draft[r.code]?.has(p.code) ?? false} onChange={() => toggle(r.code, p.code)} /></td>)}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

function Jobs() {
  const { data, reload } = useApi<{ jobName: string; startedAt: string; finishedAt?: string; status: string; itemsProcessed: number; message?: string }[]>('/settings/jobs', undefined, { interval: 30_000 });
  return (
    <Card>
      <CardHeader><div><CardTitle>Scheduled job history</CardTitle><CardDescription>Last 100 runs. Each job holds a database lock so it never runs twice at the same time.</CardDescription></div><Button size="sm" variant="ghost" onClick={reload}><RotateCw /> Refresh</Button></CardHeader>
      <CardContent className="px-0">
        {(data ?? []).length === 0 ? <EmptyState title="No job runs yet" description="Jobs start within a few minutes of server start." /> : (
          <Table><THead><TR><TH>Job</TH><TH>Started</TH><TH>Duration</TH><TH>Status</TH><TH className="text-right">Items</TH><TH>Message</TH></TR></THead>
            <TBody>{data!.map((j, i) => <TR key={i}><TD className="font-mono text-xs">{j.jobName}</TD><TD className="text-xs">{dateTime(j.startedAt)}</TD>
              <TD className="num text-xs">{j.finishedAt ? `${((new Date(j.finishedAt).getTime() - new Date(j.startedAt).getTime()) / 1000).toFixed(1)}s` : '—'}</TD>
              <TD><Badge tone={j.status === 'SUCCESS' ? 'green' : j.status === 'FAILED' ? 'red' : 'blue'}>{titleCase(j.status)}</Badge></TD><TD className="num text-right">{j.itemsProcessed}</TD><TD className="max-w-xs truncate text-xs text-muted-foreground">{j.message}</TD></TR>)}</TBody></Table>
        )}
      </CardContent>
    </Card>
  );
}

interface Mail { id: string; recipient: string; subject: string; status: string; attempts: number; maxAttempts: number; nextAttemptAt: string; lastError?: string; attachmentName?: string; createdAt: string; sentAt?: string }

function EmailLog() {
  const [page, setPage] = useState(0);
  const { data, reload } = useApi<Page<Mail>>('/notifications/email-log', { page, size: 25 });
  return (
    <Card>
      <CardHeader><div><CardTitle>Email delivery</CardTitle><CardDescription>Outgoing email queue with retry status</CardDescription></div></CardHeader>
      <CardContent className="px-0">
        {(data?.content ?? []).length === 0 ? <EmptyState title="No emails queued yet" /> : (
          <>
            <Table><THead><TR><TH>Created</TH><TH>Recipient</TH><TH>Subject</TH><TH>Status</TH><TH>Attempts</TH><TH /></TR></THead>
              <TBody>{data!.content.map((m) => <TR key={m.id}><TD className="text-xs">{dateTime(m.createdAt)}</TD><TD className="text-xs">{m.recipient}</TD><TD className="text-sm">{m.subject}{m.attachmentName && <span className="block text-xs text-muted-foreground">📎 {m.attachmentName}</span>}</TD>
                <TD><Badge tone={m.status === 'SENT' ? 'green' : m.status === 'FAILED' ? 'red' : 'amber'}>{titleCase(m.status)}</Badge>{m.lastError && <p className="max-w-xs truncate text-xs text-muted-foreground" title={m.lastError}>{m.lastError}</p>}</TD>
                <TD className="num text-xs">{m.attempts} / {m.maxAttempts}</TD>
                <TD className="text-right">{m.status === 'FAILED' && <Button size="sm" variant="outline" onClick={async () => { await api.post(`/notifications/email-log/${m.id}/retry`); toast.success('Queued for retry'); reload(); }}>Retry</Button>}</TD></TR>)}</TBody></Table>
            <Pagination page={data!.page} totalPages={data!.totalPages} total={data!.totalElements} onPage={setPage} />
          </>
        )}
      </CardContent>
    </Card>
  );
}
