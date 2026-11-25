import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Search, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
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
import { Can, EmptyState, ErrorState, PageHeader, Pagination } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { invalidateLookups, useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import type { Client, Page } from '@/types/api';

export default function ClientsPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const { data, error, loading, reload } = useApi<Page<Client>>('/clients', { q: q || undefined, status: status || undefined, page, size: 20 });
  const [editing, setEditing] = useState<Client | null | undefined>(undefined);
  return (
    <>
      <PageHeader title="Clients" description="Organisations you support, their contacts, account managers and projects."
        actions={<Can permission="CLIENT_WRITE"><Button onClick={() => setEditing(null)}><Plus /> Add client</Button></Can>} />
      <Card>
        <div className="flex flex-wrap gap-2 border-b p-3">
          <div className="relative w-full sm:w-72"><Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input className="pl-8" placeholder="Search name or code" aria-label="Search clients" value={q} onChange={(e) => { setPage(0); setQ(e.target.value); }} /></div>
          <NativeSelect aria-label="Status" className="w-36" value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Any status</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></NativeSelect>
        </div>
        {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
        {loading && !data ? <TableSkeleton /> : data && (data.content.length === 0 ? <EmptyState title="No clients found" /> : (
          <>
            <Table>
              <THead><TR><TH>Client</TH><TH>Industry</TH><TH>Account manager</TH><TH>Primary contact</TH><TH className="text-right">Projects</TH><TH className="text-right">Open / total tickets</TH><TH>Status</TH><TH /></TR></THead>
              <TBody>
                {data.content.map((c) => {
                  const primary = c.contacts.find((x) => x.primary) ?? c.contacts[0];
                  return (
                    <TR key={c.id}>
                      <TD><p className="font-medium">{c.name}</p><p className="text-xs text-muted-foreground">{c.code}</p></TD>
                      <TD>{c.industry ?? '—'}</TD><TD>{c.accountManagerName ?? '—'}</TD>
                      <TD className="text-sm">{primary ? <>{primary.name}<span className="block text-xs text-muted-foreground">{primary.email}</span></> : '—'}</TD>
                      <TD className="num text-right"><Link className="text-primary hover:underline" to={`/projects?clientId=${c.id}`}>{c.projectCount}</Link></TD>
                      <TD className="num text-right">{c.openTickets} / {c.totalTickets}</TD>
                      <TD><Badge tone={c.status === 'ACTIVE' ? 'green' : 'neutral'}>{c.status === 'ACTIVE' ? 'Active' : 'Inactive'}</Badge></TD>
                      <TD className="text-right"><Can permission="CLIENT_WRITE"><Button variant="ghost" size="sm" onClick={() => setEditing(c)}>Edit</Button></Can></TD>
                    </TR>
                  );
                })}
              </TBody>
            </Table>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ))}
      </Card>
      {editing !== undefined && <ClientDialog client={editing} onClose={(changed) => { setEditing(undefined); if (changed) { invalidateLookups(); reload(); } }} />}
    </>
  );
}

const schema = z.object({
  code: z.string().trim().min(2).max(30).regex(/^[A-Za-z0-9_-]+$/, 'Letters, numbers, - and _ only'),
  name: z.string().trim().min(2, 'Name is required').max(200),
  industry: z.string().max(100).optional(),
  email: z.union([z.literal(''), z.string().email('Enter a valid email')]).optional(),
  phone: z.string().max(40).optional(),
  website: z.string().max(200).optional(),
  address: z.string().max(500).optional(),
  accountManagerId: z.string().optional(),
  status: z.enum(['ACTIVE', 'INACTIVE']),
  configuration: z.string().max(4000).optional(),
  contacts: z.array(z.object({ name: z.string().min(1, 'Name required'), email: z.union([z.literal(''), z.string().email('Invalid email')]).optional(), phone: z.string().optional(), roleTitle: z.string().optional(), primary: z.boolean() })),
});
type Form = z.infer<typeof schema>;

function ClientDialog({ client, onClose }: { client: Client | null; onClose: (changed: boolean) => void }) {
  const lk = useLookups();
  const { register, control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { status: 'ACTIVE', contacts: [] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'contacts' });
  useEffect(() => {
    if (client) reset({ code: client.code, name: client.name, industry: client.industry ?? '', email: client.email ?? '', phone: client.phone ?? '', website: client.website ?? '',
      address: client.address ?? '', accountManagerId: client.accountManagerId ?? '', status: client.status, configuration: client.configuration ?? '',
      contacts: client.contacts.map((c) => ({ name: c.name, email: c.email ?? '', phone: c.phone ?? '', roleTitle: c.roleTitle ?? '', primary: c.primary })) });
  }, [client, reset]);
  const submit = async (v: Form) => {
    const body = { ...v, email: v.email || undefined, accountManagerId: v.accountManagerId || undefined, contacts: v.contacts.map((c) => ({ ...c, email: c.email || undefined })) };
    try {
      if (client) await api.put(`/clients/${client.id}`, body); else await api.post('/clients', body);
      toast.success(client ? 'Client updated' : 'Client added');
      onClose(true);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  return (
    <Dialog open onOpenChange={() => onClose(false)} title={client ? `Edit ${client.name}` : 'Add client'} className="max-w-2xl"
      footer={<><Button variant="outline" onClick={() => onClose(false)}>Cancel</Button><Button loading={isSubmitting} onClick={handleSubmit(submit)}>{client ? 'Save changes' : 'Add client'}</Button></>}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Code" htmlFor="c-code" error={errors.code?.message}><Input id="c-code" {...register('code')} /></Field>
        <Field label="Name" htmlFor="c-name" error={errors.name?.message}><Input id="c-name" {...register('name')} /></Field>
        <Field label="Industry" htmlFor="c-ind"><Input id="c-ind" {...register('industry')} /></Field>
        <Field label="Account manager" htmlFor="c-am"><NativeSelect id="c-am" {...register('accountManagerId')}><option value="">None</option>{lk.users.filter((u) => ['ADMIN', 'MANAGER'].includes(u.role)).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</NativeSelect></Field>
        <Field label="Email" htmlFor="c-email" error={errors.email?.message}><Input id="c-email" type="email" {...register('email')} /></Field>
        <Field label="Phone" htmlFor="c-phone"><Input id="c-phone" {...register('phone')} /></Field>
        <Field label="Website" htmlFor="c-web"><Input id="c-web" {...register('website')} /></Field>
        <Field label="Status" htmlFor="c-status"><NativeSelect id="c-status" {...register('status')}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></NativeSelect></Field>
        <Field label="Address" htmlFor="c-addr" className="sm:col-span-2"><Input id="c-addr" {...register('address')} /></Field>
        <Field label="Client-specific configuration" htmlFor="c-conf" className="sm:col-span-2" hint="Free-form notes or JSON: escalation contacts, maintenance windows, special handling"><Textarea id="c-conf" rows={3} {...register('configuration')} /></Field>
        <div className="sm:col-span-2">
          <div className="mb-2 flex items-center justify-between"><p className="text-sm font-medium">Contacts</p><Button type="button" variant="outline" size="sm" onClick={() => append({ name: '', email: '', phone: '', roleTitle: '', primary: fields.length === 0 })}><Plus /> Add contact</Button></div>
          {fields.length === 0 && <p className="text-xs text-muted-foreground">No contacts yet.</p>}
          <div className="space-y-2">
            {fields.map((f, i) => (
              <div key={f.id} className="grid gap-2 rounded-md border p-2 sm:grid-cols-[1fr_1fr_1fr_auto_auto] sm:items-center">
                <Input aria-label="Contact name" placeholder="Name" {...register(`contacts.${i}.name`)} />
                <Input aria-label="Contact email" placeholder="Email" {...register(`contacts.${i}.email`)} />
                <Input aria-label="Contact role" placeholder="Role" {...register(`contacts.${i}.roleTitle`)} />
                <label className="flex items-center gap-1.5 text-xs"><input type="checkbox" {...register(`contacts.${i}.primary`)} /> Primary</label>
                <Button type="button" variant="ghost" size="icon" aria-label="Remove contact" onClick={() => remove(i)}><Trash2 /></Button>
              </div>
            ))}
          </div>
        </div>
      </form>
    </Dialog>
  );
}
