import { CalendarClock, CheckCircle2, Filter, Play, Plus, ShieldQuestion, Trash2, XCircle, Zap } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CardGrid, FeedItem, GridCard, IconTile } from '@/components/layout';
import { useState } from 'react';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { TabsContent, TabsList, TabsTrigger, UrlTabs } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { EmptyState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import { dateTime, titleCase } from '@/utils/format';
import type { Page } from '@/types/api';

interface Rule { id: string; name: string; description?: string; triggerEvent: string; conditions: string; actions: string; projectId?: string; priority: number; stopProcessing: boolean; active: boolean }
interface Meta { triggers: string[]; fields: string[]; operators: string[]; actions: string[]; scheduledAttendanceAutomations: string[] }
interface Exec { id: string; ruleId: string; triggerEvent: string; entityId?: string; status: string; actionsRun?: string; error?: string; createdAt: string }
interface Approval { id: string; ticketId: string; status: string; reason?: string; decisionNote?: string; createdAt: string }
interface Cond { field: string; op: string; value?: string }
interface Act { type: string; [k: string]: string }

const ACTION_PARAMS: Record<string, string[]> = {
  ASSIGN_USER: ['userId'], ASSIGN_LEAST_LOADED: ['teamId?'], SET_PRIORITY: ['value'], SET_CATEGORY: ['categoryId'], AUTO_CATEGORIZE: [],
  NOTIFY_USER: ['userId', 'message'], NOTIFY_ROLE: ['role', 'message'], ESCALATE: ['level?', 'reason?'], ADD_COMMENT: ['body'],
  REQUEST_APPROVAL: ['approverId', 'reason'], SET_STATUS: ['status', 'note?'], RPA_TRIGGER: ['endpoint', 'job?'],
};

export default function AutomationPage() {
  const lk = useLookups();
  const meta = useApi<Meta>('/automation/triggers');
  const rules = useApi<Rule[]>('/automation/rules');
  const [page, setPage] = useState(0);
  const execs = useApi<Page<Exec>>('/automation/executions', { page, size: 25 });
  const approvals = useApi<Approval[]>('/automation/approvals', { status: 'PENDING' });
  const [edit, setEdit] = useState<{ id?: string; name: string; description: string; triggerEvent: string; projectId: string; priority: number; stopProcessing: boolean; active: boolean; conditions: Cond[]; actions: Act[] } | null>(null);
  const ruleName = (id: string) => rules.data?.find((r) => r.id === id)?.name ?? id.slice(0, 8);

  const open = (r?: Rule) => setEdit(r ? { id: r.id, name: r.name, description: r.description ?? '', triggerEvent: r.triggerEvent, projectId: r.projectId ?? '', priority: r.priority, stopProcessing: r.stopProcessing, active: r.active,
    conditions: JSON.parse(r.conditions), actions: JSON.parse(r.actions) } : { name: '', description: '', triggerEvent: 'TICKET_CREATED', projectId: '', priority: 100, stopProcessing: false, active: true, conditions: [], actions: [{ type: 'AUTO_CATEGORIZE' }] });
  const save = async () => {
    if (!edit) return;
    const body = { ...edit, projectId: edit.projectId || undefined, conditions: edit.conditions.map((c) => (c.op === 'in' ? { ...c, value: (c.value ?? '').split(',').map((s) => s.trim()) } : c)) };
    try { if (edit.id) await api.put(`/automation/rules/${edit.id}`, body); else await api.post('/automation/rules', body); toast.success('Rule saved'); setEdit(null); rules.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const decide = async (a: Approval, decision: string) => {
    try { await api.post(`/automation/approvals/${a.id}/decide`, { decision }); toast.success(`Request ${decision.toLowerCase()}`); approvals.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <>
      <PageHeader title="Workflow & automation" description="Rules run as trigger → conditions → actions inside the same transaction as the ticket change. Attendance automations run on schedules configured under Configuration."
        actions={<Button onClick={() => open()}><Plus /> New rule</Button>} />
      <UrlTabs fallback="rules">
        <TabsList><TabsTrigger value="rules">Rules</TabsTrigger><TabsTrigger value="runs">Execution log</TabsTrigger><TabsTrigger value="approvals">Approvals</TabsTrigger><TabsTrigger value="scheduled">Scheduled automations</TabsTrigger></TabsList>
        <TabsContent value="rules">
          {(rules.data ?? []).length === 0 ? <Card><EmptyState title="No automation rules" /></Card> : (
            <CardGrid className="lg:grid-cols-2 xl:grid-cols-2">
              {rules.data!.map((r) => {
                const conds: Cond[] = JSON.parse(r.conditions); const acts: Act[] = JSON.parse(r.actions);
                return (
                  <GridCard key={r.id} className={r.active ? '' : 'outline-dashed outline-2 -outline-offset-2 outline-muted-foreground/40 shadow-none'}>
                    <div className="flex items-start gap-3">
                      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-muted font-display text-xs font-bold text-muted-foreground" title="Order">{r.priority}</span>
                      <div className="min-w-0 flex-1"><p className="font-semibold">{r.name}</p>{r.description && <p className="text-xs text-muted-foreground">{r.description}</p>}</div>
                      <Switch checked={r.active} aria-label="Rule active" onCheckedChange={async (v) => { await api.put(`/automation/rules/${r.id}`, { ...r, conditions: conds, actions: acts, active: v }); rules.reload(); }} />
                    </div>
                    <div className="mt-4 space-y-2">
                      <FlowStep label="When" icon={Zap} tone="bg-brand text-white shadow-glow"><span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary">{titleCase(r.triggerEvent)}</span></FlowStep>
                      <FlowStep label="If" icon={Filter} tone="bg-sky/15 text-sky">
                        {conds.length === 0 ? <span className="text-xs text-muted-foreground">Always</span> : conds.map((c, i) => <span key={i} className="rounded-full bg-sky/10 px-2.5 py-1 font-mono text-[11px]">{c.field} {c.op} {Array.isArray(c.value) ? c.value.join('|') : c.value ?? ''}</span>)}
                      </FlowStep>
                      <FlowStep label="Then" icon={Play} tone="bg-ok/15 text-ok" last>
                        {acts.map((a, i) => <span key={i} className="rounded-full bg-ok/10 px-2.5 py-1 text-xs font-semibold text-ok">{titleCase(a.type)}</span>)}
                        {r.stopProcessing && <span className="rounded-full bg-muted px-2.5 py-1 text-xs">then stop</span>}
                      </FlowStep>
                    </div>
                    <div className="mt-auto flex justify-end gap-1 pt-3"><Button variant="ghost" size="sm" onClick={() => open(r)}>Edit</Button>
                      <Button variant="ghost" size="icon" aria-label="Delete rule" onClick={async () => { if (window.confirm('Delete this rule?')) { await api.delete(`/automation/rules/${r.id}`); rules.reload(); } }}><Trash2 /></Button></div>
                  </GridCard>
                );
              })}
            </CardGrid>
          )}
        </TabsContent>
        <TabsContent value="runs">
          <Card><CardContent className="pt-4">
            {(execs.data?.content ?? []).length === 0 ? <EmptyState title="No executions yet" /> : (
              <ul className="space-y-0.5">
                {(execs.data?.content ?? []).map((x) => (
                  <FeedItem key={x.id} icon={x.status === 'FAILED' ? XCircle : CheckCircle2} tone={x.status === 'FAILED' ? 'bg-breach/10 text-breach' : 'bg-ok/10 text-ok'}
                    title={<><span className="font-semibold">{ruleName(x.ruleId)}</span> <span className="text-muted-foreground">on</span> {titleCase(x.triggerEvent)}</>}
                    meta={x.error ?? x.actionsRun}
                    right={<div className="flex flex-col items-end gap-1"><Badge tone={x.status === 'FAILED' ? 'red' : 'green'}>{titleCase(x.status)}</Badge><span className="display-num text-xs text-muted-foreground">{dateTime(x.createdAt)}</span></div>} />
                ))}
              </ul>
            )}
            {execs.data && <Pagination page={execs.data.page} totalPages={execs.data.totalPages} total={execs.data.totalElements} onPage={setPage} />}
          </CardContent></Card>
        </TabsContent>
        <TabsContent value="approvals">
          <Card><CardHeader><div><CardTitle>Waiting for your decision</CardTitle><CardDescription>Created by rules with a request approval action</CardDescription></div></CardHeader>
            <CardContent>
              {(approvals.data ?? []).length === 0 ? <EmptyState title="Nothing waiting for approval" /> : (
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{approvals.data!.map((a) => (
                  <div key={a.id} className="flex flex-col rounded-2xl bg-muted/50 p-4">
                    <div className="flex items-center gap-3"><IconTile icon={ShieldQuestion} tone="amber" /><div><p className="font-semibold">Approval requested</p><p className="text-xs text-muted-foreground">{dateTime(a.createdAt)}</p></div></div>
                    <p className="mt-3 text-sm">{a.reason ?? 'No reason given'}</p>
                    <Link className="mt-1 text-sm font-medium text-primary hover:underline" to={`/tickets/${a.ticketId}`}>Open ticket</Link>
                    <div className="mt-auto flex gap-2 pt-3"><Button size="sm" onClick={() => decide(a, 'APPROVED')}>Approve</Button><Button size="sm" variant="outline" onClick={() => decide(a, 'REJECTED')}>Reject</Button></div>
                  </div>
                ))}</div>
              )}
            </CardContent></Card>
        </TabsContent>
        <TabsContent value="scheduled">
          <Card><CardHeader><div><CardTitle>Scheduled attendance and SLA automations</CardTitle><CardDescription>Run by the server with distributed locks so each runs once, and catch up after restarts. Thresholds come from attendance settings.</CardDescription></div></CardHeader>
            <CardContent><ul className="grid gap-2 sm:grid-cols-2">{(meta.data?.scheduledAttendanceAutomations ?? []).map((s) => <SchedItem key={s} text={s} />)}
              <SchedItem text="SLA breach detection and percentage escalations" /><SchedItem text="Pending-ticket reminders and auto-close of resolved tickets" /></ul></CardContent></Card>
        </TabsContent>
      </UrlTabs>

      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)} title={edit?.id ? 'Edit rule' : 'New rule'} className="max-w-3xl"
        footer={<><Button variant="outline" onClick={() => setEdit(null)}>Cancel</Button><Button disabled={!edit?.name || !edit.actions.length} onClick={save}>Save rule</Button></>}>
        {edit && meta.data && (
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Name" htmlFor="r-name"><Input id="r-name" value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
              <Field label="When" htmlFor="r-trig"><NativeSelect id="r-trig" value={edit.triggerEvent} onChange={(e) => setEdit({ ...edit, triggerEvent: e.target.value })}>{meta.data.triggers.map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}</NativeSelect></Field>
              <Field label="Project" htmlFor="r-proj"><NativeSelect id="r-proj" value={edit.projectId} onChange={(e) => setEdit({ ...edit, projectId: e.target.value })}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
              <Field label="Order (lower runs first)" htmlFor="r-prio"><Input id="r-prio" type="number" value={edit.priority} onChange={(e) => setEdit({ ...edit, priority: Number(e.target.value) })} /></Field>
              <Field label="Description" htmlFor="r-desc" className="sm:col-span-2"><Textarea id="r-desc" rows={2} value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} /></Field>
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between"><p className="text-sm font-medium">If all of these are true</p><Button size="sm" variant="outline" onClick={() => setEdit({ ...edit, conditions: [...edit.conditions, { field: 'priority', op: 'eq', value: '' }] })}><Plus /> Condition</Button></div>
              {edit.conditions.length === 0 && <p className="text-xs text-muted-foreground">No conditions: the rule runs for every event.</p>}
              {edit.conditions.map((c, i) => (
                <div key={i} className="mb-2 grid grid-cols-[1fr_8rem_1fr_auto] gap-2">
                  <NativeSelect aria-label="Field" value={c.field} onChange={(e) => setEdit({ ...edit, conditions: edit.conditions.map((x, j) => (j === i ? { ...x, field: e.target.value } : x)) })}>{meta.data!.fields.map((f) => <option key={f} value={f}>{f}</option>)}</NativeSelect>
                  <NativeSelect aria-label="Operator" value={c.op} onChange={(e) => setEdit({ ...edit, conditions: edit.conditions.map((x, j) => (j === i ? { ...x, op: e.target.value } : x)) })}>{meta.data!.operators.map((o) => <option key={o} value={o}>{o}</option>)}</NativeSelect>
                  <Input aria-label="Value" placeholder={c.op === 'in' ? 'comma separated' : 'value'} disabled={c.op === 'empty' || c.op === 'notEmpty'} value={Array.isArray(c.value) ? (c.value as string[]).join(', ') : c.value ?? ''} onChange={(e) => setEdit({ ...edit, conditions: edit.conditions.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)) })} />
                  <Button variant="ghost" size="icon" aria-label="Remove condition" onClick={() => setEdit({ ...edit, conditions: edit.conditions.filter((_, j) => j !== i) })}><Trash2 /></Button>
                </div>
              ))}
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between"><p className="text-sm font-medium">Then do</p><Button size="sm" variant="outline" onClick={() => setEdit({ ...edit, actions: [...edit.actions, { type: 'NOTIFY_ROLE', role: 'TEAM_LEADER', message: '' }] })}><Plus /> Action</Button></div>
              {edit.actions.map((a, i) => (
                <div key={i} className="mb-2 flex flex-wrap items-center gap-2 rounded-md border p-2">
                  <NativeSelect aria-label="Action" className="w-48" value={a.type} onChange={(e) => setEdit({ ...edit, actions: edit.actions.map((x, j) => (j === i ? { type: e.target.value } : x)) })}>{meta.data!.actions.map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}</NativeSelect>
                  {(ACTION_PARAMS[a.type] ?? []).map((p) => {
                    const key = p.replace('?', '');
                    if (key === 'userId' || key === 'approverId') return <NativeSelect key={key} aria-label={key} className="w-44" value={a[key] ?? ''} onChange={(e) => setEdit({ ...edit, actions: edit.actions.map((x, j) => (j === i ? { ...x, [key]: e.target.value } : x)) })}><option value="">Choose person</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect>;
                    if (key === 'teamId') return <NativeSelect key={key} aria-label="Team" className="w-44" value={a[key] ?? ''} onChange={(e) => setEdit({ ...edit, actions: edit.actions.map((x, j) => (j === i ? { ...x, teamId: e.target.value } : x)) })}><option value="">Ticket's team</option>{lk.teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</NativeSelect>;
                    return <Input key={key} aria-label={key} className="w-44 flex-1" placeholder={key} value={a[key] ?? ''} onChange={(e) => setEdit({ ...edit, actions: edit.actions.map((x, j) => (j === i ? { ...x, [key]: e.target.value } : x)) })} />;
                  })}
                  <Button variant="ghost" size="icon" aria-label="Remove action" onClick={() => setEdit({ ...edit, actions: edit.actions.filter((_, j) => j !== i) })}><Trash2 /></Button>
                </div>
              ))}
            </div>
            <div className="flex gap-6">
              <label className="flex items-center gap-2 text-sm"><Switch checked={edit.stopProcessing} onCheckedChange={(v) => setEdit({ ...edit, stopProcessing: v })} /> Stop processing later rules</label>
              <label className="flex items-center gap-2 text-sm"><Switch checked={edit.active} onCheckedChange={(v) => setEdit({ ...edit, active: v })} /> Active</label>
            </div>
          </div>
        )}
      </Dialog>
    </>
  );
}

function FlowStep({ label, icon: Icon, tone, children, last }: { label: string; icon: LucideIcon; tone: string; children: ReactNode; last?: boolean }) {
  return (
    <div className="relative flex gap-3">
      {!last && <span className="absolute left-[15px] top-8 h-[calc(100%-16px)] w-px border-l border-dashed border-border" />}
      <span className={`relative grid h-8 w-8 shrink-0 place-items-center rounded-lg ${tone}`}><Icon className="h-3.5 w-3.5" /></span>
      <div className="min-w-0 flex-1 pb-1"><p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{label}</p><div className="mt-1 flex flex-wrap gap-1.5">{children}</div></div>
    </div>
  );
}

function SchedItem({ text }: { text: string }) {
  return <li className="flex items-center gap-3 rounded-xl bg-muted/50 px-3 py-2.5 text-sm"><span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"><CalendarClock className="h-4 w-4" /></span>{text}</li>;
}
