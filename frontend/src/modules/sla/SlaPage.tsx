import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { Can, EmptyState, PageHeader, PriorityTag } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import { date, titleCase } from '@/utils/format';
import type { Priority } from '@/types/api';

interface Policy { id: string; name: string; projectId?: string; priority: Priority; responseMinutes: number; resolutionMinutes: number; calendarId?: string; businessHoursOnly: boolean; pauseOnPending: boolean; active: boolean }
interface Calendar { id: string; name: string; timezone: string; defaultCalendar: boolean; hours: { dayOfWeek: number; startTime: string; endTime: string }[]; holidays: { id: string; holidayDate: string; name: string }[] }
interface Escalation { id: string; projectId?: string; priority?: string; triggerType: string; thresholdPercent?: number; escalationLevel: number; notifyRole?: string; active: boolean }
const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const mins = (m: number) => (m % 1440 === 0 ? `${m / 1440}d` : m % 60 === 0 ? `${m / 60}h` : `${m}m`);

export default function SlaPage() {
  const lk = useLookups();
  const policies = useApi<Policy[]>('/sla/policies');
  const calendars = useApi<Calendar[]>('/sla/calendars');
  const escalations = useApi<Escalation[]>('/sla/escalation-rules');
  const [edit, setEdit] = useState<Partial<Policy> | null>(null);
  const [hol, setHol] = useState<{ calendarId: string; date: string; name: string } | null>(null);
  const [esc, setEsc] = useState<Partial<Escalation> | null>(null);
  const projectName = (id?: string) => (id ? lk.projects.find((p) => p.id === id)?.name ?? 'Project' : 'All projects');

  const savePolicy = async () => {
    try {
      if (edit?.id) await api.put(`/sla/policies/${edit.id}`, edit); else await api.post('/sla/policies', edit);
      toast.success('SLA policy saved'); setEdit(null); policies.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const deactivate = async (p: Policy) => {
    try { await api.delete(`/sla/policies/${p.id}`); toast.success('Policy deactivated'); policies.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const saveCalendarHours = async (c: Calendar, day: number, on: boolean) => {
    const hours = on ? [...c.hours, { dayOfWeek: day, startTime: '09:00', endTime: '18:00' }] : c.hours.filter((h) => h.dayOfWeek !== day);
    try { await api.put(`/sla/calendars/${c.id}`, { name: c.name, timezone: c.timezone, hours }); calendars.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const updateHours = async (c: Calendar, day: number, key: 'startTime' | 'endTime', v: string) => {
    const hours = c.hours.map((h) => (h.dayOfWeek === day ? { ...h, [key]: v } : h));
    try { await api.put(`/sla/calendars/${c.id}`, { name: c.name, timezone: c.timezone, hours }); calendars.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <>
      <PageHeader title="SLA management" description="Response and resolution targets by priority and project, measured on business calendars. All calculations run on the server."
        actions={<Can permission="SLA_WRITE"><Button onClick={() => setEdit({ priority: 'MEDIUM', responseMinutes: 240, resolutionMinutes: 1440, businessHoursOnly: true, pauseOnPending: true })}><Plus /> New policy</Button></Can>} />
      <Card>
        <CardHeader><div><CardTitle>Policies</CardTitle><CardDescription>A project policy overrides the organisation-wide policy for the same priority</CardDescription></div></CardHeader>
        <CardContent className="px-0">
          {(policies.data ?? []).length === 0 ? <EmptyState title="No SLA policies" description="Tickets have no SLA clock until a policy exists for their priority." /> : (
            <Table>
              <THead><TR><TH>Policy</TH><TH>Scope</TH><TH>Priority</TH><TH>First response</TH><TH>Resolution</TH><TH>Clock</TH><TH>Pending</TH><TH /></TR></THead>
              <TBody>
                {(policies.data ?? []).map((p) => (
                  <TR key={p.id} className={p.active ? '' : 'opacity-50'}>
                    <TD className="font-medium">{p.name}{!p.active && <Badge className="ml-2">Inactive</Badge>}</TD><TD>{projectName(p.projectId)}</TD><TD><PriorityTag priority={p.priority} /></TD>
                    <TD className="num">{mins(p.responseMinutes)}</TD><TD className="num">{mins(p.resolutionMinutes)}</TD>
                    <TD className="text-xs">{p.businessHoursOnly ? 'Business hours' : '24×7'}</TD><TD className="text-xs">{p.pauseOnPending ? 'Pauses clock' : 'Keeps running'}</TD>
                    <TD className="whitespace-nowrap text-right"><Can permission="SLA_WRITE"><Button variant="ghost" size="sm" onClick={() => setEdit(p)}>Edit</Button>{p.active && <Button variant="ghost" size="sm" onClick={() => deactivate(p)}>Deactivate</Button>}</Can></TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        {(calendars.data ?? []).map((c) => (
          <Card key={c.id}>
            <CardHeader><div><CardTitle>{c.name}</CardTitle><CardDescription>{c.timezone}{c.defaultCalendar ? ' · default calendar' : ''}{c.hours.length === 0 ? ' · no hours set, treated as 24×7' : ''}</CardDescription></div></CardHeader>
            <CardContent>
              <div className="space-y-1.5">
                {DAYS.map((d, i) => {
                  const h = c.hours.find((x) => x.dayOfWeek === i + 1);
                  return (
                    <div key={d} className="flex items-center gap-3 text-sm">
                      <Can permission="SLA_WRITE" fallback={<span className="w-9" />}><Switch checked={!!h} onCheckedChange={(on) => saveCalendarHours(c, i + 1, on)} aria-label={`${d} business day`} /></Can>
                      <span className="w-10">{d}</span>
                      {h ? <><Input type="time" aria-label={`${d} start`} className="h-8 w-28" defaultValue={h.startTime.slice(0, 5)} onBlur={(e) => e.target.value !== h.startTime.slice(0, 5) && updateHours(c, i + 1, 'startTime', e.target.value)} />
                        <span className="text-muted-foreground">to</span>
                        <Input type="time" aria-label={`${d} end`} className="h-8 w-28" defaultValue={h.endTime.slice(0, 5)} onBlur={(e) => e.target.value !== h.endTime.slice(0, 5) && updateHours(c, i + 1, 'endTime', e.target.value)} /></>
                        : <span className="text-muted-foreground">Closed</span>}
                    </div>
                  );
                })}
              </div>
              <div className="mt-4 border-t pt-3">
                <div className="mb-2 flex items-center justify-between"><p className="text-sm font-medium">Holidays</p><Can permission="SLA_WRITE"><Button size="sm" variant="outline" onClick={() => setHol({ calendarId: c.id, date: '', name: '' })}><Plus /> Add holiday</Button></Can></div>
                {c.holidays.length === 0 ? <p className="text-xs text-muted-foreground">No holidays.</p> : (
                  <ul className="space-y-1 text-sm">{c.holidays.map((h) => <li key={h.id} className="flex items-center justify-between"><span>{date(h.holidayDate)} · {h.name}</span>
                    <Can permission="SLA_WRITE"><Button variant="ghost" size="icon" aria-label={`Remove ${h.name}`} onClick={async () => { await api.delete(`/sla/holidays/${h.id}`); calendars.reload(); }}><Trash2 /></Button></Can></li>)}</ul>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="mt-4">
        <CardHeader><div><CardTitle>Escalation rules</CardTitle><CardDescription>Automatic escalation on breach or when a share of the resolution time has elapsed</CardDescription></div>
          <Can permission="SLA_WRITE"><Button size="sm" variant="outline" onClick={() => setEsc({ triggerType: 'RESOLUTION_PERCENT', thresholdPercent: 75, escalationLevel: 1, notifyRole: 'TEAM_LEADER', active: true })}><Plus /> Add rule</Button></Can></CardHeader>
        <CardContent className="px-0">
          <Table>
            <THead><TR><TH>Trigger</TH><TH>Scope</TH><TH>Priority</TH><TH>Level</TH><TH>Notify</TH><TH /></TR></THead>
            <TBody>{(escalations.data ?? []).map((e) => (
              <TR key={e.id}><TD>{titleCase(e.triggerType)}{e.thresholdPercent ? ` at ${e.thresholdPercent}%` : ''}</TD><TD>{projectName(e.projectId)}</TD><TD>{e.priority ? titleCase(e.priority) : 'Any'}</TD>
                <TD>L{e.escalationLevel}</TD><TD>{e.notifyRole ? titleCase(e.notifyRole) : '—'}</TD>
                <TD className="text-right"><Can permission="SLA_WRITE"><Button variant="ghost" size="icon" aria-label="Delete rule" onClick={async () => { await api.delete(`/sla/escalation-rules/${e.id}`); escalations.reload(); }}><Trash2 /></Button></Can></TD></TR>
            ))}</TBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)} title={edit?.id ? 'Edit SLA policy' : 'New SLA policy'}
        footer={<><Button variant="outline" onClick={() => setEdit(null)}>Cancel</Button><Button onClick={savePolicy}>Save policy</Button></>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Name" htmlFor="sp-name" className="sm:col-span-2"><Input id="sp-name" value={edit.name ?? ''} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="Project" htmlFor="sp-proj"><NativeSelect id="sp-proj" value={edit.projectId ?? ''} onChange={(e) => setEdit({ ...edit, projectId: e.target.value || undefined })}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
            <Field label="Priority" htmlFor="sp-pri"><NativeSelect id="sp-pri" value={edit.priority} onChange={(e) => setEdit({ ...edit, priority: e.target.value as Priority })}>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}</NativeSelect></Field>
            <Field label="First response (minutes)" htmlFor="sp-resp"><Input id="sp-resp" type="number" min={1} value={edit.responseMinutes ?? ''} onChange={(e) => setEdit({ ...edit, responseMinutes: Number(e.target.value) })} /></Field>
            <Field label="Resolution (minutes)" htmlFor="sp-res"><Input id="sp-res" type="number" min={1} value={edit.resolutionMinutes ?? ''} onChange={(e) => setEdit({ ...edit, resolutionMinutes: Number(e.target.value) })} /></Field>
            <Field label="Calendar" htmlFor="sp-cal" className="sm:col-span-2"><NativeSelect id="sp-cal" value={edit.calendarId ?? ''} onChange={(e) => setEdit({ ...edit, calendarId: e.target.value || undefined })}><option value="">Project calendar, else default</option>{(calendars.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect></Field>
            <label className="flex items-center gap-2 text-sm"><Switch checked={!!edit.businessHoursOnly} onCheckedChange={(v) => setEdit({ ...edit, businessHoursOnly: v })} /> Business hours only</label>
            <label className="flex items-center gap-2 text-sm"><Switch checked={!!edit.pauseOnPending} onCheckedChange={(v) => setEdit({ ...edit, pauseOnPending: v })} /> Pause while pending</label>
          </div>
        )}
      </Dialog>
      <Dialog open={!!hol} onOpenChange={(o) => !o && setHol(null)} title="Add holiday"
        footer={<><Button variant="outline" onClick={() => setHol(null)}>Cancel</Button><Button disabled={!hol?.date || !hol.name} onClick={async () => { try { await api.post(`/sla/calendars/${hol!.calendarId}/holidays`, { date: hol!.date, name: hol!.name }); setHol(null); calendars.reload(); } catch (e) { toast.error(errorMessage(e)); } }}>Add</Button></>}>
        {hol && <div className="grid gap-3 sm:grid-cols-2"><Field label="Date" htmlFor="h-date"><Input id="h-date" type="date" value={hol.date} onChange={(e) => setHol({ ...hol, date: e.target.value })} /></Field>
          <Field label="Name" htmlFor="h-name"><Input id="h-name" value={hol.name} onChange={(e) => setHol({ ...hol, name: e.target.value })} /></Field></div>}
      </Dialog>
      <Dialog open={!!esc} onOpenChange={(o) => !o && setEsc(null)} title="Add escalation rule"
        footer={<><Button variant="outline" onClick={() => setEsc(null)}>Cancel</Button><Button onClick={async () => { try { await api.post('/sla/escalation-rules', esc); setEsc(null); escalations.reload(); } catch (e) { toast.error(errorMessage(e)); } }}>Add rule</Button></>}>
        {esc && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Trigger" htmlFor="e-trig"><NativeSelect id="e-trig" value={esc.triggerType} onChange={(e) => setEsc({ ...esc, triggerType: e.target.value })}><option value="RESOLUTION_PERCENT">Share of resolution time elapsed</option><option value="RESPONSE_BREACH">Response SLA breached</option><option value="RESOLUTION_BREACH">Resolution SLA breached</option></NativeSelect></Field>
            {esc.triggerType === 'RESOLUTION_PERCENT' && <Field label="Threshold %" htmlFor="e-pct"><Input id="e-pct" type="number" min={1} max={100} value={esc.thresholdPercent ?? ''} onChange={(e) => setEsc({ ...esc, thresholdPercent: Number(e.target.value) })} /></Field>}
            <Field label="Escalation level" htmlFor="e-lvl"><Input id="e-lvl" type="number" min={1} max={5} value={esc.escalationLevel ?? 1} onChange={(e) => setEsc({ ...esc, escalationLevel: Number(e.target.value) })} /></Field>
            <Field label="Notify role" htmlFor="e-role"><NativeSelect id="e-role" value={esc.notifyRole ?? ''} onChange={(e) => setEsc({ ...esc, notifyRole: e.target.value || undefined })}><option value="">None</option>{['TEAM_LEADER', 'MANAGER', 'L3'].map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</NativeSelect></Field>
            <Field label="Project" htmlFor="e-proj"><NativeSelect id="e-proj" value={esc.projectId ?? ''} onChange={(e) => setEsc({ ...esc, projectId: e.target.value || undefined })}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
          </div>
        )}
      </Dialog>
    </>
  );
}
