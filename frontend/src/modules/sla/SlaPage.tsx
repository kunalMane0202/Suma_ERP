import { BellRing, CalendarDays, Plus, Timer, Trash2 } from 'lucide-react';
import { CardGrid, GridCard, HeroBanner, IconTile } from '@/components/layout';
import { priorityColors } from '@/utils/chart';
import { useTheme } from '@/contexts/ThemeContext';
import { useState } from 'react';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
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
  const prio = priorityColors(useTheme().theme === 'DARK');
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
      <HeroBanner icon={Timer} title="Service levels" subtitle="Targets measured on business calendars"
        metrics={[{ label: 'Active policies', value: (policies.data ?? []).filter((p) => p.active).length }, { label: 'Calendars', value: (calendars.data ?? []).length },
          { label: 'Holidays', value: (calendars.data ?? []).reduce((a, c) => a + c.holidays.length, 0) }, { label: 'Escalation rules', value: (escalations.data ?? []).length }]} />
      <h2 className="mb-1 font-display text-lg font-semibold">Policies</h2>
      <p className="mb-3 text-sm text-muted-foreground">A project policy overrides the organisation-wide policy for the same priority</p>
      {(policies.data ?? []).length === 0 ? <Card><EmptyState title="No SLA policies" description="Tickets have no SLA clock until a policy exists for their priority." /></Card> : (
        <CardGrid className="xl:grid-cols-4">
          {(policies.data ?? []).map((p) => (
            <GridCard key={p.id} className={`relative overflow-hidden ${p.active ? '' : 'outline-dashed outline-2 -outline-offset-2 outline-muted-foreground/40 shadow-none'}`}>
              <span className="absolute inset-x-0 top-0 h-1.5" style={{ background: prio[p.priority] }} />
              <div className="flex items-start justify-between gap-2"><PriorityTag priority={p.priority} />{!p.active && <Badge>Inactive</Badge>}</div>
              <p className="mt-2 font-semibold leading-snug">{p.name}</p>
              <p className="text-xs text-muted-foreground">{projectName(p.projectId)}</p>
              <div className="mt-4 grid grid-cols-2 gap-2">
                <div className="rounded-xl bg-muted/70 px-3 py-2"><p className="text-[11px] text-muted-foreground">First response</p><p className="display-num text-2xl">{mins(p.responseMinutes)}</p></div>
                <div className="rounded-xl bg-muted/70 px-3 py-2"><p className="text-[11px] text-muted-foreground">Resolution</p><p className="display-num text-2xl">{mins(p.resolutionMinutes)}</p></div>
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5 text-xs">
                <span className="rounded-full bg-sky/15 px-2.5 py-1 font-semibold text-sky">{p.businessHoursOnly ? 'Business hours' : '24×7'}</span>
                <span className="rounded-full bg-idle/15 px-2.5 py-1 font-semibold text-idle">{p.pauseOnPending ? 'Pauses clock' : 'Keeps running'}</span>
              </div>
              <div className="mt-auto flex justify-end gap-1 pt-3"><Can permission="SLA_WRITE"><Button variant="ghost" size="sm" onClick={() => setEdit(p)}>Edit</Button>{p.active && <Button variant="ghost" size="sm" onClick={() => deactivate(p)}>Deactivate</Button>}</Can></div>
            </GridCard>
          ))}
        </CardGrid>
      )}

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        {(calendars.data ?? []).map((c) => (
          <Card key={c.id}>
            <CardHeader><div className="flex items-center gap-3"><IconTile icon={CalendarDays} tone="sky" /><div><CardTitle>{c.name}</CardTitle><CardDescription>{c.timezone}{c.defaultCalendar ? ' · default calendar' : ''}{c.hours.length === 0 ? ' · no hours set, treated as 24×7' : ''}</CardDescription></div></div></CardHeader>
            <CardContent>
              <div className="mb-4 grid grid-cols-7 gap-1.5">
                {DAYS.map((d, i) => { const h = c.hours.find((x) => x.dayOfWeek === i + 1); return (
                  <div key={d} className={`rounded-xl py-2 text-center ${h ? 'bg-brand text-white shadow-glow' : 'bg-muted text-muted-foreground'}`}>
                    <p className="text-xs font-semibold">{d}</p><p className="display-num mt-0.5 text-[10px]">{h ? `${h.startTime.slice(0, 5)}` : 'off'}</p></div>); })}
              </div>
              <div className="space-y-1.5">
                {DAYS.map((d, i) => {
                  const h = c.hours.find((x) => x.dayOfWeek === i + 1);
                  return (
                    <div key={d} className="flex flex-wrap items-center gap-3 rounded-xl px-2 py-1 text-sm hover:bg-accent/50">
                      <Can permission="SLA_WRITE" fallback={<span className="w-9" />}><Switch checked={!!h} onCheckedChange={(on) => saveCalendarHours(c, i + 1, on)} aria-label={`${d} business day`} /></Can>
                      <span className="w-10">{d}</span>
                      {h ? <><Input type="time" aria-label={`${d} start`} className="h-8 w-28 rounded-full" defaultValue={h.startTime.slice(0, 5)} onBlur={(e) => e.target.value !== h.startTime.slice(0, 5) && updateHours(c, i + 1, 'startTime', e.target.value)} />
                        <span className="text-muted-foreground">to</span>
                        <Input type="time" aria-label={`${d} end`} className="h-8 w-28 rounded-full" defaultValue={h.endTime.slice(0, 5)} onBlur={(e) => e.target.value !== h.endTime.slice(0, 5) && updateHours(c, i + 1, 'endTime', e.target.value)} /></>
                        : <span className="text-muted-foreground">Closed</span>}
                    </div>
                  );
                })}
              </div>
              <div className="mt-4 border-t pt-3">
                <div className="mb-2 flex items-center justify-between"><p className="text-sm font-medium">Holidays</p><Can permission="SLA_WRITE"><Button size="sm" variant="outline" onClick={() => setHol({ calendarId: c.id, date: '', name: '' })}><Plus /> Add holiday</Button></Can></div>
                {c.holidays.length === 0 ? <p className="text-xs text-muted-foreground">No holidays.</p> : (
                  <ul className="flex flex-wrap gap-2 text-sm">{c.holidays.map((h) => <li key={h.id} className="flex items-center gap-1 rounded-full bg-warn/10 py-0.5 pl-3 pr-1"><span><span className="font-semibold">{h.name}</span> <span className="text-xs text-muted-foreground">{date(h.holidayDate)}</span></span>
                    <Can permission="SLA_WRITE"><Button variant="ghost" size="icon" className="h-7 w-7" aria-label={`Remove ${h.name}`} onClick={async () => { if (!window.confirm(`Remove the holiday ${h.name}?`)) return; await api.delete(`/sla/holidays/${h.id}`); calendars.reload(); }}><Trash2 /></Button></Can></li>)}</ul>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="mt-4">
        <CardHeader><div><CardTitle>Escalation rules</CardTitle><CardDescription>Automatic escalation on breach or when a share of the resolution time has elapsed</CardDescription></div>
          <Can permission="SLA_WRITE"><Button size="sm" variant="outline" onClick={() => setEsc({ triggerType: 'RESOLUTION_PERCENT', thresholdPercent: 75, escalationLevel: 1, notifyRole: 'TEAM_LEADER', active: true })}><Plus /> Add rule</Button></Can></CardHeader>
        <CardContent>
          {(escalations.data ?? []).length === 0 ? <EmptyState title="No escalation rules" /> : (
            <ol className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {[...(escalations.data ?? [])].sort((x, y) => x.escalationLevel - y.escalationLevel).map((e) => (
                <li key={e.id} className="relative flex gap-3 rounded-2xl bg-muted/50 p-4">
                  <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl font-display text-sm font-bold text-white ${e.triggerType === 'RESOLUTION_PERCENT' ? 'bg-gradient-to-br from-[#F5A524] to-[#F0506E]' : 'bg-gradient-to-br from-[#F0506E] to-[#A78BFA]'}`}>L{e.escalationLevel}</span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">{titleCase(e.triggerType)}{e.thresholdPercent ? ` at ${e.thresholdPercent}%` : ''}</p>
                    <p className="text-xs text-muted-foreground">{projectName(e.projectId)} · {e.priority ? titleCase(e.priority) : 'Any priority'}</p>
                    <p className="mt-2 inline-flex items-center gap-1 rounded-full bg-card px-2.5 py-0.5 text-xs font-medium shadow-glass"><BellRing className="h-3 w-3" /> {e.notifyRole ? titleCase(e.notifyRole) : 'No notification'}</p>
                  </div>
                  <Can permission="SLA_WRITE"><Button variant="ghost" size="icon" aria-label="Delete rule" onClick={async () => { if (!window.confirm('Delete this escalation rule?')) return; await api.delete(`/sla/escalation-rules/${e.id}`); escalations.reload(); }}><Trash2 /></Button></Can>
                </li>
              ))}
            </ol>
          )}
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
