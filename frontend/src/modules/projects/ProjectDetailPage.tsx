import { ArrowLeft, CheckCircle2, CircleAlert, FolderKanban, Pencil, Plus, Trash2 } from 'lucide-react';
import { HeroBanner, KeyValue } from '@/components/layout';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input, NativeSelect } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';
import { Avatar, Can, ErrorState } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { invalidateLookups, useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { api, errorMessage } from '@/services/api';
import { date, titleCase } from '@/utils/format';
import type { Category, Project } from '@/types/api';
import { ProjectDialog } from './ProjectsPage';

export default function ProjectDetailPage() {
  const { id } = useParams();
  const { can } = useAuth();
  const lk = useLookups();
  const { data: p, error, reload } = useApi<Project>(`/projects/${id}`);
  const cats = useApi<Category[]>(`/projects/${id}/categories`);
  const [edit, setEdit] = useState(false);
  const [addMember, setAddMember] = useState(false);
  const [member, setMember] = useState({ userId: '', designation: 'MEMBER', allocationPercent: 100 });
  const [cat, setCat] = useState({ name: '', parentId: '', keywords: '' });
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!p) return <Skeleton className="h-96" />;

  const verify = async (v: boolean) => {
    try {
      await api.post(`/projects/${id}/lead-email/verify`, null, { params: { verified: v } });
      toast.success(v ? 'Project Lead email verified' : 'Verification removed');
      reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const saveMember = async () => {
    try {
      await api.put(`/projects/${id}/members`, member);
      toast.success('Allocation saved');
      setAddMember(false);
      invalidateLookups();
      reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const removeMember = async (userId: string) => {
    if (!window.confirm('Remove this person from the project?')) return;
    try { await api.delete(`/projects/${id}/members/${userId}`); reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const addCategory = async () => {
    try {
      await api.post(`/projects/${id}/categories`, { name: cat.name, parentId: cat.parentId || undefined, keywords: cat.keywords || undefined });
      setCat({ name: '', parentId: '', keywords: '' });
      cats.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <>
      <Link to="/projects" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" /> Projects</Link>
      <HeroBanner icon={FolderKanban} title={p.name} subtitle={`${p.code} · ${p.clientName} · ${p.supportHours ?? 'support hours not set'} · ${p.timezone}`}
        metrics={[{ label: 'People', value: p.memberCount }, { label: 'Open tickets', value: p.openTickets }, { label: 'All tickets', value: p.totalTickets }, { label: 'Categories', value: (cats.data ?? []).length }]}
        action={<Can permission="PROJECT_WRITE"><Button variant="outline" className="bg-white/15 text-white shadow-none hover:bg-white/25" onClick={() => setEdit(true)}><Pencil /> Edit project</Button></Can>} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader><CardTitle>Leadership</CardTitle></CardHeader>
          <CardContent>
            <ul className="space-y-2">
              {([['Manager', p.managerName], ['Team leader', p.teamLeaderName], ['Project Lead', p.projectLeadName]] as [string, string | undefined][]).map(([r, n]) => (
                <li key={r} className="flex items-center gap-3 rounded-xl bg-muted/60 px-3 py-2.5">
                  {n ? <Avatar name={n} /> : <span className="h-9 w-9 rounded-full bg-muted" />}
                  <div><p className="text-xs text-muted-foreground">{r}</p><p className="font-semibold">{n ?? 'Not set'}</p></div>
                </li>
              ))}
            </ul>
            <KeyValue className="mt-3" items={[['Status', <Badge tone={p.status === 'ACTIVE' ? 'green' : 'neutral'}>{titleCase(p.status)}</Badge>], ['Period', `${date(p.startDate)} – ${p.endDate ? date(p.endDate) : 'ongoing'}`]]} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader><div><CardTitle>Monthly report recipient</CardTitle><CardDescription>Attendance reports are only emailed to a verified address</CardDescription></div></CardHeader>
          <CardContent>
            {p.projectLeadEmail ? (
              <div className="space-y-3">
                <p className="flex items-center gap-2 text-sm">{p.projectLeadEmailVerified ? <CheckCircle2 className="h-4 w-4 text-ok" /> : <CircleAlert className="h-4 w-4 text-warn" />}{p.projectLeadEmail}</p>
                <p className="text-xs text-muted-foreground">{p.projectLeadEmailVerified ? 'Verified. The report for the previous month is sent on the 1st.' : 'Not verified. Confirm the address belongs to the Project Lead before verifying.'}</p>
                {can('PROJECT_WRITE') && (p.projectLeadEmailVerified
                  ? <Button size="sm" variant="outline" onClick={() => verify(false)}>Remove verification</Button>
                  : <Button size="sm" onClick={() => verify(true)}>Mark as verified</Button>)}
              </div>
            ) : <p className="text-sm text-muted-foreground">No Project Lead email configured. Edit the project to add one.</p>}
          </CardContent>
        </Card>
        <Card tone="tint">
          <CardHeader><CardTitle>Ticket queue</CardTitle></CardHeader>
          <CardContent>
            <p className="display-num text-[44px] leading-none">{p.openTickets}</p><p className="text-sm text-muted-foreground">open of {p.totalTickets} tickets</p>
            <Button asChild className="mt-4"><Link to={`/tickets?projectId=${p.id}`}>Open queue</Link></Button>
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader><div><CardTitle>People allocated</CardTitle><CardDescription>Designations drive data access and Project Lead reporting</CardDescription></div>
            <Can permission="PROJECT_WRITE"><Button size="sm" variant="outline" onClick={() => setAddMember(true)}><Plus /> Allocate</Button></Can></CardHeader>
          <CardContent className="px-0">
            <Table>
              <THead><TR><TH>Name</TH><TH>Role</TH><TH>Designation</TH><TH className="text-right">Allocation</TH><TH>Primary</TH><TH /></TR></THead>
              <TBody>
                {(p.members ?? []).map((m) => (
                  <TR key={m.userId}><TD><div className="flex items-center gap-3"><Avatar name={m.fullName} className="h-8 w-8 text-[10px]" /><div><p className="font-semibold">{m.fullName}</p><p className="text-xs text-muted-foreground">{m.employeeCode}</p></div></div></TD><TD>{m.role}</TD>
                    <TD><Badge tone={m.designation === 'PROJECT_LEAD' ? 'navy' : m.designation === 'MEMBER' ? 'neutral' : 'blue'}>{titleCase(m.designation)}</Badge></TD>
                    <TD className="num text-right">{m.allocationPercent}%</TD><TD>{m.primary ? 'Yes' : ''}</TD>
                    <TD className="text-right"><Can permission="PROJECT_WRITE"><Button variant="ghost" size="icon" aria-label={`Remove ${m.fullName}`} onClick={() => window.confirm(`Remove ${m.fullName} from this project?`) && removeMember(m.userId)}><Trash2 /></Button></Can></TD></TR>
                ))}
              </TBody>
            </Table>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><div><CardTitle>Categories</CardTitle><CardDescription>Keywords power automatic categorisation</CardDescription></div></CardHeader>
          <CardContent>
            <ul className="space-y-1.5 text-sm">
              {(cats.data ?? []).filter((c) => !c.parentId).map((c) => (
                <li key={c.id} className="rounded-xl bg-muted/60 p-3"><span className="font-semibold">{c.name}</span>{c.keywords && <span className="mt-0.5 block text-xs text-muted-foreground">{c.keywords}</span>}
                  <div className="mt-2 flex flex-wrap gap-1">{(cats.data ?? []).filter((s) => s.parentId === c.id).map((s) => <span key={s.id} className="rounded-full bg-card px-2.5 py-0.5 text-xs font-medium shadow-glass">{s.name}</span>)}</div></li>
              ))}
            </ul>
            <Can permission="PROJECT_WRITE">
              <div className="mt-4 space-y-2 border-t pt-3">
                <Input aria-label="Category name" placeholder="New category or subcategory…" value={cat.name} onChange={(e) => setCat({ ...cat, name: e.target.value })} />
                <NativeSelect aria-label="Parent category" value={cat.parentId} onChange={(e) => setCat({ ...cat, parentId: e.target.value })}><option value="">Top level</option>{(cats.data ?? []).filter((c) => !c.parentId).map((c) => <option key={c.id} value={c.id}>Under {c.name}</option>)}</NativeSelect>
                <Input aria-label="Keywords" placeholder="Keywords, comma separated…" value={cat.keywords} onChange={(e) => setCat({ ...cat, keywords: e.target.value })} />
                <Button size="sm" disabled={!cat.name.trim()} onClick={addCategory}>Add category</Button>
              </div>
            </Can>
          </CardContent>
        </Card>
      </div>
      {edit && <ProjectDialog project={p} onClose={(c) => { setEdit(false); if (c) reload(); }} />}
      <Dialog open={addMember} onOpenChange={setAddMember} title="Allocate a person" footer={<><Button variant="outline" onClick={() => setAddMember(false)}>Cancel</Button><Button disabled={!member.userId} onClick={saveMember}>Save</Button></>}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Person" htmlFor="m-user" className="sm:col-span-2"><NativeSelect id="m-user" value={member.userId} onChange={(e) => setMember({ ...member, userId: e.target.value })}><option value="">Choose</option>{lk.users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.role})</option>)}</NativeSelect></Field>
          <Field label="Designation" htmlFor="m-des"><NativeSelect id="m-des" value={member.designation} onChange={(e) => setMember({ ...member, designation: e.target.value })}>{['MEMBER', 'TEAM_LEADER', 'MANAGER', 'PROJECT_LEAD'].map((d) => <option key={d} value={d}>{titleCase(d)}</option>)}</NativeSelect></Field>
          <Field label="Allocation %" htmlFor="m-alloc"><Input id="m-alloc" type="number" min={0} max={100} value={member.allocationPercent} onChange={(e) => setMember({ ...member, allocationPercent: Number(e.target.value) })} /></Field>
        </div>
      </Dialog>
    </>
  );
}
