import { Plug, Plus, RefreshCw, Trash2, Zap } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { EmptyState, PageHeader } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import { dateTime, relative } from '@/utils/format';

interface Integration { id: string; name: string; connectorType: string; projectId: string; projectName?: string; baseUrl?: string; authType: string; credentialKeys: string[]; query?: string; fieldMapping?: Record<string, unknown>; syncIntervalMinutes: number; maxRetries: number; enabled: boolean; lastSyncAt?: string; lastStatus?: string; consecutiveFailures: number }
interface Log { id: string; startedAt: string; status: string; attempt: number; fetched: number; created: number; duplicates: number; failed: number; error?: string }
const CRED_KEYS: Record<string, string[]> = { NONE: [], BASIC: ['username', 'password'], BEARER: ['token'], OAUTH2: ['token'], API_KEY: ['headerName', 'apiKey'] };

export default function IntegrationsPage() {
  const lk = useLookups();
  const { data, reload } = useApi<Integration[]>('/integrations');
  const mappings = useApi<Record<string, Record<string, unknown>>>('/integrations/connectors');
  const [edit, setEdit] = useState<{ id?: string; name: string; connectorType: string; projectId: string; baseUrl: string; authType: string; credentials: Record<string, string>; query: string; mapping: string; syncIntervalMinutes: number; maxRetries: number; enabled: boolean } | null>(null);
  const [logsFor, setLogsFor] = useState<Integration | null>(null);
  const logs = useApi<Log[]>(logsFor ? `/integrations/${logsFor.id}/logs` : null);
  const [busy, setBusy] = useState<string | null>(null);

  const open = (i?: Integration) => setEdit(i ? { id: i.id, name: i.name, connectorType: i.connectorType, projectId: i.projectId, baseUrl: i.baseUrl ?? '', authType: i.authType, credentials: {}, query: i.query ?? '',
    mapping: i.fieldMapping ? JSON.stringify(i.fieldMapping, null, 2) : '', syncIntervalMinutes: i.syncIntervalMinutes, maxRetries: i.maxRetries, enabled: i.enabled }
    : { name: '', connectorType: 'JIRA', projectId: '', baseUrl: '', authType: 'BASIC', credentials: {}, query: '', mapping: '', syncIntervalMinutes: 15, maxRetries: 3, enabled: false });
  const save = async () => {
    if (!edit) return;
    let fieldMapping;
    try { fieldMapping = edit.mapping.trim() ? JSON.parse(edit.mapping) : undefined; } catch { toast.error('Field mapping must be valid JSON'); return; }
    const creds = Object.fromEntries(Object.entries(edit.credentials).filter(([, v]) => v));
    try {
      const body = { ...edit, fieldMapping, credentials: Object.keys(creds).length ? creds : undefined };
      if (edit.id) await api.put(`/integrations/${edit.id}`, body); else await api.post('/integrations', body);
      toast.success('Integration saved'); setEdit(null); reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const run = async (i: Integration, what: 'test' | 'sync') => {
    setBusy(i.id + what);
    try {
      const r = await api.post(`/integrations/${i.id}/${what}`);
      if (what === 'test') toast.success(r.data.message);
      else toast[r.data.status === 'FAILED' ? 'error' : 'success'](r.data.status === 'FAILED' ? `Sync failed: ${r.data.error}` : `Sync ${r.data.status.toLowerCase()}: ${r.data.created} created, ${r.data.duplicates} duplicates skipped`);
      reload();
    } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(null); }
  };

  return (
    <>
      <PageHeader title="Integrations" description="Ingest tickets from Jira, Salesforce, any REST API or a support mailbox. Credentials are encrypted at rest and never shown again after saving."
        actions={<Button onClick={() => open()}><Plus /> New integration</Button>} />
      {(data ?? []).length === 0 ? <Card><EmptyState icon={<Plug />} title="No integrations configured" description="Connect a source system to create tickets automatically. Duplicate external tickets are always skipped." action={<Button onClick={() => open()}><Plus /> New integration</Button>} /></Card> : (
        <div className="grid gap-4 lg:grid-cols-2">
          {data!.map((i) => (
            <Card key={i.id}>
              <CardHeader>
                <div><CardTitle>{i.name}</CardTitle><CardDescription>{i.connectorType} → {i.projectName} · every {i.syncIntervalMinutes} min · {i.maxRetries} retries</CardDescription></div>
                <Badge tone={!i.enabled ? 'neutral' : i.lastStatus === 'FAILED' ? 'red' : i.lastStatus === 'PARTIAL' ? 'amber' : 'green'}>{!i.enabled ? 'Disabled' : i.lastStatus ?? 'Not run yet'}</Badge>
              </CardHeader>
              <CardContent>
                <p className="truncate font-mono text-xs text-muted-foreground">{i.baseUrl}</p>
                <p className="mt-1 text-xs text-muted-foreground">Auth {i.authType}{i.credentialKeys.length ? ` · stored: ${i.credentialKeys.join(', ')}` : ' · no credentials stored'} · last sync {i.lastSyncAt ? relative(i.lastSyncAt) : 'never'}{i.consecutiveFailures ? ` · ${i.consecutiveFailures} consecutive failures` : ''}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" loading={busy === i.id + 'test'} onClick={() => run(i, 'test')}><Zap /> Test connection</Button>
                  <Button size="sm" variant="outline" loading={busy === i.id + 'sync'} onClick={() => run(i, 'sync')}><RefreshCw /> Sync now</Button>
                  <Button size="sm" variant="ghost" onClick={() => setLogsFor(i)}>Sync log</Button>
                  <Button size="sm" variant="ghost" onClick={() => open(i)}>Edit</Button>
                  <Button size="icon" variant="ghost" aria-label="Delete integration" onClick={async () => { if (window.confirm('Delete this integration?')) { await api.delete(`/integrations/${i.id}`); reload(); } }}><Trash2 /></Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)} title={edit?.id ? 'Edit integration' : 'New integration'} className="max-w-2xl"
        footer={<><Button variant="outline" onClick={() => setEdit(null)}>Cancel</Button><Button disabled={!edit?.name || !edit?.projectId} onClick={save}>Save integration</Button></>}>
        {edit && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Name" htmlFor="i-name"><Input id="i-name" value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="Connector" htmlFor="i-type"><NativeSelect id="i-type" value={edit.connectorType} onChange={(e) => setEdit({ ...edit, connectorType: e.target.value, authType: e.target.value === 'EMAIL' ? 'BASIC' : edit.authType })}><option value="JIRA">Jira</option><option value="SALESFORCE">Salesforce</option><option value="REST">Custom REST API</option><option value="EMAIL">Email (IMAP)</option></NativeSelect></Field>
            <Field label="Create tickets in project" htmlFor="i-proj"><NativeSelect id="i-proj" value={edit.projectId} onChange={(e) => setEdit({ ...edit, projectId: e.target.value })}><option value="">Choose</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
            <Field label={edit.connectorType === 'EMAIL' ? 'Mailbox URL' : 'Base URL'} htmlFor="i-url" hint={edit.connectorType === 'EMAIL' ? 'imaps://mail.example.com:993/INBOX' : 'https://...'}><Input id="i-url" value={edit.baseUrl} onChange={(e) => setEdit({ ...edit, baseUrl: e.target.value })} /></Field>
            <Field label="Authentication" htmlFor="i-auth"><NativeSelect id="i-auth" value={edit.authType} onChange={(e) => setEdit({ ...edit, authType: e.target.value, credentials: {} })}>{Object.keys(CRED_KEYS).map((k) => <option key={k} value={k}>{k}</option>)}</NativeSelect></Field>
            {CRED_KEYS[edit.authType].map((k) => (
              <Field key={k} label={k} htmlFor={`i-c-${k}`} hint={edit.id ? 'Leave empty to keep the stored value' : undefined}>
                <Input id={`i-c-${k}`} type={/password|token|apiKey/.test(k) ? 'password' : 'text'} autoComplete="off" value={edit.credentials[k] ?? ''} onChange={(e) => setEdit({ ...edit, credentials: { ...edit.credentials, [k]: e.target.value } })} />
              </Field>
            ))}
            <Field label={edit.connectorType === 'JIRA' ? 'JQL query' : edit.connectorType === 'SALESFORCE' ? 'SOQL query' : 'Path / query string'} htmlFor="i-q" className="sm:col-span-2"><Input id="i-q" value={edit.query} onChange={(e) => setEdit({ ...edit, query: e.target.value })} /></Field>
            <Field label="Field mapping (JSON, optional)" htmlFor="i-map" className="sm:col-span-2" hint={`Default: ${JSON.stringify(mappings.data?.[edit.connectorType] ?? {})}`}><Textarea id="i-map" rows={4} className="font-mono text-xs" value={edit.mapping} onChange={(e) => setEdit({ ...edit, mapping: e.target.value })} /></Field>
            <Field label="Sync every (minutes)" htmlFor="i-int"><Input id="i-int" type="number" min={1} value={edit.syncIntervalMinutes} onChange={(e) => setEdit({ ...edit, syncIntervalMinutes: Number(e.target.value) })} /></Field>
            <Field label="Retries per sync" htmlFor="i-ret"><Input id="i-ret" type="number" min={0} max={10} value={edit.maxRetries} onChange={(e) => setEdit({ ...edit, maxRetries: Number(e.target.value) })} /></Field>
            <label className="flex items-center gap-2 text-sm"><Switch checked={edit.enabled} onCheckedChange={(v) => setEdit({ ...edit, enabled: v })} /> Enabled (scheduled sync)</label>
          </div>
        )}
      </Dialog>
      <Dialog open={!!logsFor} onOpenChange={(o) => !o && setLogsFor(null)} title={`Sync log · ${logsFor?.name ?? ''}`} className="max-w-3xl">
        <Table>
          <THead><TR><TH>Started</TH><TH>Attempt</TH><TH>Status</TH><TH className="text-right">Fetched</TH><TH className="text-right">Created</TH><TH className="text-right">Duplicates</TH><TH>Error</TH></TR></THead>
          <TBody>{(logs.data ?? []).map((l) => <TR key={l.id}><TD className="text-xs">{dateTime(l.startedAt)}</TD><TD>{l.attempt}</TD><TD><Badge tone={l.status === 'SUCCESS' ? 'green' : l.status === 'FAILED' ? 'red' : 'amber'}>{l.status}</Badge></TD>
            <TD className="num text-right">{l.fetched}</TD><TD className="num text-right">{l.created}</TD><TD className="num text-right">{l.duplicates}</TD><TD className="max-w-xs truncate text-xs text-muted-foreground" title={l.error}>{l.error}</TD></TR>)}</TBody>
        </Table>
        {(logs.data ?? []).length === 0 && <p className="py-4 text-center text-sm text-muted-foreground">No syncs yet.</p>}
      </Dialog>
    </>
  );
}
