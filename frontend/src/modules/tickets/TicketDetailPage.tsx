import { ArrowLeft, Download, Paperclip, Send, ShieldAlert, UserPlus } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { NativeSelect, Textarea, Input } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Avatar, Can, ErrorState, PriorityTag, SlaBadge, StatusBadge } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { api, download, errorMessage } from '@/services/api';
import { dateTime, relative, titleCase } from '@/utils/format';
import type { ProjectMember, Project, TicketDetail, TicketStatus } from '@/types/api';

export default function TicketDetailPage() {
  const { id } = useParams();
  const { data, error, loading, reload, setData } = useApi<TicketDetail>(`/tickets/${id}`);
  const project = useApi<Project>(data ? `/projects/${data.summary.projectId}` : null);
  const [comment, setComment] = useState('');
  const [internal, setInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [transition, setTransition] = useState<TicketStatus | null>(null);
  const [note, setNote] = useState('');
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignee, setAssignee] = useState('');
  const [escalateOpen, setEscalateOpen] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (loading || !data) return <div className="space-y-4"><Skeleton className="h-10 w-2/3" /><Skeleton className="h-64" /></div>;
  const t = data.summary;

  const act = async (fn: () => Promise<{ data: TicketDetail }>, ok: string) => {
    setBusy(true);
    try {
      const r = await fn();
      setData(r.data);
      toast.success(ok);
      return true;
    } catch (e) {
      toast.error(errorMessage(e));
      return false;
    } finally {
      setBusy(false);
    }
  };
  const addComment = async () => {
    setBusy(true);
    try {
      await api.post(`/tickets/${id}/comments`, { body: comment, internal });
      setComment('');
      toast.success(internal ? 'Internal note added' : 'Comment added');
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const upload = async (f: File) => {
    const fd = new FormData();
    fd.append('file', f);
    try {
      await api.post(`/tickets/${id}/attachments`, fd);
      toast.success('Attachment uploaded');
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  const members: ProjectMember[] = project.data?.members ?? [];
  const needsNote = transition === 'RESOLVED' || transition === 'REOPENED' || transition === 'PENDING';

  return (
    <>
      <Link to="/tickets" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" /> Tickets</Link>
      <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="num text-sm text-muted-foreground">{t.ticketNumber} · {t.projectName} · {t.clientName}</p>
          <h1 className="mt-1 text-[22px] font-semibold leading-snug">{t.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2"><StatusBadge status={t.status} /><PriorityTag priority={t.priority} /><SlaBadge sla={t.sla} />
            {t.escalationLevel > 0 && <Badge tone="red">Escalated L{t.escalationLevel}</Badge>}<Badge>{titleCase(t.source)}</Badge></div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Can permission="TICKET_UPDATE">
            {data.allowedTransitions.map((s) => (
              <Button key={s} size="sm" variant={s === 'RESOLVED' ? 'default' : 'outline'} onClick={() => { setTransition(s); setNote(''); }}>
                {s === 'IN_PROGRESS' ? 'Start work' : s === 'RESOLVED' ? 'Resolve' : s === 'CLOSED' ? 'Close' : s === 'REOPENED' ? 'Reopen' : s === 'PENDING' ? 'Set pending' : `Move to ${titleCase(s).toLowerCase()}`}
              </Button>
            ))}
          </Can>
          <Can permission="TICKET_ASSIGN"><Button size="sm" variant="outline" onClick={() => { setAssignee(t.assigneeId ?? ''); setAssignOpen(true); }}><UserPlus /> Assign</Button></Can>
          <Can permission="TICKET_ESCALATE"><Button size="sm" variant="outline" onClick={() => { setNote(''); setEscalateOpen(true); }}><ShieldAlert /> Escalate</Button></Can>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="space-y-4 xl:col-span-2">
          <Card>
            <CardHeader><CardTitle>Description</CardTitle></CardHeader>
            <CardContent><p className="whitespace-pre-wrap text-sm leading-relaxed">{data.description || <span className="text-muted-foreground">No description.</span>}</p></CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Conversation</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {data.comments.length === 0 && <p className="text-sm text-muted-foreground">No comments yet.</p>}
              {data.comments.map((c) => (
                <div key={c.id} className={`flex gap-3 rounded-md p-3 ${c.internal ? 'border border-warn/30 bg-warn/5' : 'bg-muted/40'}`}>
                  <Avatar name={c.authorName ?? 'System'} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm"><span className="font-medium">{c.authorName ?? 'Automation'}</span> <span className="text-xs text-muted-foreground">{relative(c.createdAt)}{c.internal ? ' · internal note' : ''}</span></p>
                    <p className="mt-1 whitespace-pre-wrap text-sm">{c.body}</p>
                  </div>
                </div>
              ))}
              <Can permission="TICKET_UPDATE">
                <div className="space-y-2 border-t pt-4">
                  <Textarea aria-label="Add a comment" placeholder="Write a reply or internal note" value={comment} onChange={(e) => setComment(e.target.value)} />
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="flex items-center gap-2 text-sm"><Switch checked={internal} onCheckedChange={setInternal} aria-label="Internal note" /> Internal note</label>
                    <div className="flex gap-2">
                      <input ref={fileRef} type="file" className="hidden" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
                      <Button variant="outline" size="sm" onClick={() => fileRef.current?.click()}><Paperclip /> Attach file</Button>
                      <Button size="sm" disabled={!comment.trim()} loading={busy} onClick={addComment}><Send /> Post</Button>
                    </div>
                  </div>
                </div>
              </Can>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Activity timeline</CardTitle></CardHeader>
            <CardContent>
              <ol className="relative space-y-3 border-l pl-5">
                {data.history.map((h) => (
                  <li key={h.id} className="relative text-sm">
                    <span className="absolute -left-[25px] top-1.5 h-2 w-2 rounded-full bg-primary ring-4 ring-card" />
                    <p><span className="font-medium">{h.actorName ?? 'System'}</span> {describe(h)}</p>
                    {h.note && <p className="text-xs text-muted-foreground">{h.note}</p>}
                    <p className="text-xs text-muted-foreground">{dateTime(h.createdAt)}</p>
                  </li>
                ))}
              </ol>
            </CardContent>
          </Card>
        </div>
        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle>Details</CardTitle></CardHeader>
            <CardContent>
              <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
                <dt className="text-muted-foreground">Assignee</dt><dd>{t.assigneeName ?? 'Unassigned'}</dd>
                <dt className="text-muted-foreground">Team</dt><dd>{t.teamName ?? '—'}</dd>
                <dt className="text-muted-foreground">Category</dt><dd>{t.categoryName ?? '—'}</dd>
                <dt className="text-muted-foreground">Reporter</dt><dd>{data.reporterName ?? '—'}{data.reporterEmail && <span className="block text-xs text-muted-foreground">{data.reporterEmail}</span>}</dd>
                <dt className="text-muted-foreground">Created</dt><dd>{dateTime(t.createdAt)}</dd>
                <dt className="text-muted-foreground">Age</dt><dd className="num">{t.ageHours} h</dd>
                {data.externalId && <><dt className="text-muted-foreground">External ref</dt><dd>{data.externalSystem}: {data.externalId}</dd></>}
                {data.reopenedCount > 0 && <><dt className="text-muted-foreground">Reopened</dt><dd>{data.reopenedCount}×</dd></>}
              </dl>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>SLA</CardTitle></CardHeader>
            <CardContent>
              <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
                <dt className="text-muted-foreground">Policy</dt><dd>{t.sla.policyName ?? 'None'}</dd>
                <dt className="text-muted-foreground">Response due</dt><dd className={t.sla.responseBreached ? 'text-breach' : ''}>{dateTime(t.sla.responseDueAt)}</dd>
                <dt className="text-muted-foreground">First response</dt><dd>{dateTime(t.sla.firstResponseAt)}</dd>
                <dt className="text-muted-foreground">Resolution due</dt><dd className={t.sla.resolutionBreached ? 'text-breach' : ''}>{dateTime(t.sla.resolutionDueAt)}</dd>
                <dt className="text-muted-foreground">Resolved</dt><dd>{dateTime(data.resolvedAt)}</dd>
              </dl>
              {t.sla.paused && <p className="mt-3 rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">The SLA clock is paused while this ticket is pending.</p>}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Attachments</CardTitle></CardHeader>
            <CardContent>
              {data.attachments.length === 0 ? <p className="text-sm text-muted-foreground">No attachments.</p> : (
                <ul className="space-y-1.5">
                  {data.attachments.map((a) => (
                    <li key={a.id} className="flex items-center justify-between gap-2 text-sm">
                      <span className="truncate">{a.fileName} <span className="text-xs text-muted-foreground">({Math.ceil(a.sizeBytes / 1024)} KB)</span></span>
                      <Button variant="ghost" size="icon" aria-label={`Download ${a.fileName}`} onClick={() => download(`/tickets/${id}/attachments/${a.id}`).catch((e) => toast.error(errorMessage(e)))}><Download /></Button>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={!!transition} onOpenChange={(o) => !o && setTransition(null)} title={transition ? `Move to ${titleCase(transition).toLowerCase()}` : ''}
        description={needsNote ? 'Add a note explaining this change. It is posted to the conversation.' : undefined}
        footer={<><Button variant="outline" onClick={() => setTransition(null)}>Cancel</Button>
          <Button loading={busy} disabled={needsNote && !note.trim()} onClick={async () => { if (await act(() => api.post(`/tickets/${id}/status`, { status: transition, note: note || undefined }), 'Status updated')) setTransition(null); }}>Confirm</Button></>}>
        <Field label={needsNote ? 'Note (required)' : 'Note (optional)'} htmlFor="tr-note"><Textarea id="tr-note" value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      </Dialog>
      <Dialog open={assignOpen} onOpenChange={setAssignOpen} title="Assign ticket" description="Only people allocated to this project can be assigned."
        footer={<><Button variant="outline" onClick={() => setAssignOpen(false)}>Cancel</Button>
          <Button loading={busy} onClick={async () => { if (await act(() => api.post(`/tickets/${id}/assign`, { assigneeId: assignee || null }), 'Assignment updated')) setAssignOpen(false); }}>Save</Button></>}>
        <Field label="Assignee" htmlFor="as-user">
          <NativeSelect id="as-user" value={assignee} onChange={(e) => setAssignee(e.target.value)}>
            <option value="">Unassigned</option>{members.map((m) => <option key={m.userId} value={m.userId}>{m.fullName} ({m.role})</option>)}
          </NativeSelect>
        </Field>
      </Dialog>
      <Dialog open={escalateOpen} onOpenChange={setEscalateOpen} title={`Escalate to level ${t.escalationLevel + 1}`} description="The team leader and, from level 2, the project manager are notified."
        footer={<><Button variant="outline" onClick={() => setEscalateOpen(false)}>Cancel</Button>
          <Button variant="destructive" loading={busy} disabled={!note.trim()} onClick={async () => { if (await act(() => api.post(`/tickets/${id}/escalate`, { reason: note }), 'Ticket escalated')) setEscalateOpen(false); }}>Escalate</Button></>}>
        <Field label="Reason" htmlFor="esc-reason"><Input id="esc-reason" value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      </Dialog>
    </>
  );
}

function describe(h: TicketDetail['history'][number]) {
  switch (h.action) {
    case 'CREATED': return 'created the ticket';
    case 'STATUS_CHANGED': return <>changed status from <b>{titleCase(h.oldValue)}</b> to <b>{titleCase(h.newValue)}</b></>;
    case 'ASSIGNED': return <>assigned it to <b>{h.newValue ?? 'nobody'}</b></>;
    case 'REASSIGNED': return <>reassigned it from {h.oldValue ?? 'nobody'} to <b>{h.newValue ?? 'nobody'}</b></>;
    case 'ESCALATED': return <>escalated it to level <b>{h.newValue}</b></>;
    case 'SLA_BREACHED': return <span className="text-breach">— {h.field} SLA breached</span>;
    case 'FIRST_RESPONSE': return 'recorded the first response';
    case 'COMMENTED': return h.note === 'Internal note added' ? 'added an internal note' : 'commented';
    case 'ATTACHMENT_ADDED': return <>attached {h.newValue}</>;
    case 'UPDATED': return <>changed {h.field}{h.oldValue || h.newValue ? <> from {h.oldValue ?? '—'} to <b>{h.newValue ?? '—'}</b></> : ''}</>;
    default: return titleCase(h.action).toLowerCase();
  }
}
