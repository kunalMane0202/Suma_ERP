import { ArrowLeft, BookMarked, CircleHelp, Eye, Pencil, Plus, Search, ThumbsUp, Wrench, CheckCheck } from 'lucide-react';
import { motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Can, EmptyState, ErrorState, PageHeader } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { api, errorMessage } from '@/services/api';
import { relative, titleCase } from '@/utils/format';

interface Row { id: string; title: string; summary?: string; articleType: string; categoryId?: string; categoryName?: string; projectId?: string; projectName?: string; tags?: string; status: string; authorName?: string; viewCount: number; helpfulCount: number; updatedAt: string; snippet?: string }
interface Detail { article: Row; content: string }
interface Cat { id: string; name: string }

const TYPE_STYLE: Record<string, { label: string; band: string; icon: typeof BookMarked }> = {
  SOP: { label: 'SOP', band: 'bg-gradient-to-br from-[#5B4CF0] to-[#8B5CF6]', icon: BookMarked },
  TROUBLESHOOTING: { label: 'Troubleshooting', band: 'bg-gradient-to-br from-[#F5A524] to-[#F0506E]', icon: Wrench },
  FAQ: { label: 'FAQ', band: 'bg-gradient-to-br from-[#2E9BF0] to-[#5B4CF0]', icon: CircleHelp },
  RESOLUTION: { label: 'Resolution', band: 'bg-gradient-to-br from-[#12B886] to-[#2E9BF0]', icon: CheckCheck },
};

export default function KnowledgePage() {
  const [q, setQ] = useState('');
  const [deb, setDeb] = useState('');
  const [type, setType] = useState('');
  const [cat, setCat] = useState('');
  useEffect(() => { const h = setTimeout(() => setDeb(q), 300); return () => clearTimeout(h); }, [q]);
  const { data, error, loading, reload } = useApi<Row[]>('/knowledge/articles', { q: deb || undefined, type: type || undefined, categoryId: cat || undefined });
  const cats = useApi<Cat[]>('/knowledge/categories');
  const [open, setOpen] = useState<string | null>(null);
  const [edit, setEdit] = useState<Partial<Detail['article'] & { content: string }> | null>(null);

  if (open) return <Article id={open} onBack={() => { setOpen(null); reload(); }} onEdit={(d) => setEdit({ ...d.article, content: d.content })} />;
  return (
    <>
      <PageHeader title="Knowledge base" description="SOPs, troubleshooting guides, FAQs and proven resolutions, searchable across title, tags and content."
        actions={<Can permission="KB_WRITE"><Button onClick={() => setEdit({ articleType: 'SOP', status: 'DRAFT' })}><Plus /> New article</Button></Can>} />
      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative w-full sm:w-96"><Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input className="pl-8" placeholder="Search, e.g. vpn disconnect" aria-label="Search knowledge base" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <NativeSelect aria-label="Type" className="w-44" value={type} onChange={(e) => setType(e.target.value)}><option value="">All types</option>{['SOP', 'TROUBLESHOOTING', 'FAQ', 'RESOLUTION'].map((t) => <option key={t} value={t}>{t === 'SOP' ? 'SOP' : titleCase(t)}</option>)}</NativeSelect>
        <NativeSelect aria-label="Category" className="w-52" value={cat} onChange={(e) => setCat(e.target.value)}><option value="">All categories</option>{(cats.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect>
      </div>
      {error && <ErrorState message={error} onRetry={reload} />}
      {loading && !data ? <div className="grid gap-3 md:grid-cols-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-32" />)}</div> : (data ?? []).length === 0 ? (
        <Card><EmptyState title={deb ? `No articles match “${deb}”` : 'No articles yet'} description="Try different words, or write the article your team is missing." /></Card>
      ) : (
        <div className="columns-1 gap-4 md:columns-2 2xl:columns-3 [column-fill:_balance]">
          {data!.map((a, i) => {
            const t = TYPE_STYLE[a.articleType] ?? TYPE_STYLE.FAQ;
            return (
              <motion.button key={a.id} onClick={() => setOpen(a.id)} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }}
                transition={{ delay: Math.min(i, 10) * 0.04, type: 'spring', stiffness: 240, damping: 24 }} whileHover={{ y: -4 }}
                className="glass mb-4 block w-full break-inside-avoid overflow-hidden text-left transition-shadow hover:shadow-lift">
                <div className={`relative h-20 ${t.band} p-4`}>
                  <span className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-xl bg-white/25 text-white backdrop-blur"><t.icon className="h-4 w-4" /></span>
                  <span className="inline-flex rounded-full bg-white/25 px-2.5 py-0.5 text-[11px] font-bold text-white backdrop-blur">{t.label}</span>
                </div>
                <div className="p-5">
                  <div className="mb-1.5 flex flex-wrap items-center gap-1.5">
                    {a.status !== 'PUBLISHED' && <Badge>{titleCase(a.status)}</Badge>}
                    {a.projectName && <span className="text-xs text-muted-foreground">{a.projectName}</span>}
                  </div>
                  <p className="font-display text-[16px] font-semibold leading-snug">{a.title}</p>
                  <p className="mt-2 text-sm text-muted-foreground">{a.snippet ? <Highlight text={a.snippet} /> : a.summary}</p>
                  {a.tags && <div className="mt-3 flex flex-wrap gap-1">{a.tags.split(',').slice(0, 4).map((tg) => <span key={tg} className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">#{tg.trim()}</span>)}</div>}
                  <div className="mt-4 flex items-center justify-between text-xs text-muted-foreground">
                    <span>{a.categoryName ?? 'Uncategorised'}</span>
                    <span className="flex items-center gap-3"><span className="inline-flex items-center gap-1"><Eye className="h-3.5 w-3.5" />{a.viewCount}</span><span className="inline-flex items-center gap-1"><ThumbsUp className="h-3.5 w-3.5" />{a.helpfulCount}</span></span>
                  </div>
                </div>
              </motion.button>
            );
          })}
        </div>
      )}
      {edit && <Editor value={edit} cats={cats.data ?? []} onClose={(saved) => { setEdit(null); if (saved) { reload(); if (open) setOpen(null); } }} />}
    </>
  );
}

function Highlight({ text }: { text: string }) {
  const parts = text.split(/(\[\[.*?\]\])/g);
  return <>{parts.map((p, i) => (p.startsWith('[[') ? <mark key={i} className="rounded bg-warn/20 px-0.5 text-foreground">{p.slice(2, -2)}</mark> : <span key={i}>{p}</span>))}</>;
}

function Article({ id, onBack, onEdit }: { id: string; onBack: () => void; onEdit: (d: Detail) => void }) {
  const { data, error } = useApi<Detail>(`/knowledge/articles/${id}`);
  const [helped, setHelped] = useState(false);
  if (error) return <ErrorState message={error} />;
  if (!data) return <Skeleton className="h-96" />;
  const a = data.article;
  return (
    <>
      <button onClick={onBack} className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" /> Knowledge base</button>
      <Card className="mx-auto max-w-3xl">
        <CardContent className="py-6">
          <div className="flex items-start justify-between gap-3">
            <div><p className="text-xs text-muted-foreground">{a.articleType === 'SOP' ? 'SOP' : titleCase(a.articleType)} · {a.categoryName ?? 'Uncategorised'}{a.projectName ? ` · ${a.projectName}` : ''}</p>
              <h1 className="mt-1 text-2xl font-semibold">{a.title}</h1>
              <p className="mt-1 text-xs text-muted-foreground">By {a.authorName ?? 'unknown'} · updated {relative(a.updatedAt)} · {a.viewCount} views</p></div>
            <Can permission="KB_WRITE"><Button variant="outline" size="sm" onClick={() => onEdit(data)}><Pencil /> Edit</Button></Can>
          </div>
          <div className="mt-6 max-w-[72ch] space-y-3 text-[15px] leading-7">{renderMarkdown(data.content)}</div>
          {a.tags && <div className="mt-6 flex flex-wrap gap-1">{a.tags.split(',').map((t) => <Badge key={t}>{t.trim()}</Badge>)}</div>}
          <div className="mt-6 border-t pt-4">
            <Button variant="outline" size="sm" disabled={helped} onClick={async () => { await api.post(`/knowledge/articles/${id}/helpful`); setHelped(true); toast.success('Thanks for the feedback'); }}>
              <ThumbsUp /> {helped ? 'Marked helpful' : 'This helped me'}</Button>
          </div>
        </CardContent>
      </Card>
    </>
  );
}

/** Minimal, safe markdown: headings, ordered/unordered lists, paragraphs. React escapes all text. */
function renderMarkdown(md: string) {
  const blocks = md.replace(/\r/g, '').split(/\n{2,}/);
  return blocks.map((b, i) => {
    const lines = b.split('\n');
    if (b.startsWith('## ')) return <h2 key={i} className="pt-2 text-lg font-semibold">{b.slice(3)}</h2>;
    if (b.startsWith('# ')) return <h2 key={i} className="pt-2 text-xl font-semibold">{b.slice(2)}</h2>;
    if (lines.every((l) => /^\d+\.\s/.test(l))) return <ol key={i} className="list-decimal space-y-1 pl-6">{lines.map((l, j) => <li key={j}>{l.replace(/^\d+\.\s/, '')}</li>)}</ol>;
    if (lines.every((l) => /^[-*]\s/.test(l))) return <ul key={i} className="list-disc space-y-1 pl-6">{lines.map((l, j) => <li key={j}>{l.slice(2)}</li>)}</ul>;
    if (lines[0].startsWith('## ')) return <div key={i}><h2 className="pt-2 text-lg font-semibold">{lines[0].slice(3)}</h2>{renderMarkdown(lines.slice(1).join('\n'))}</div>;
    return <p key={i} className="whitespace-pre-wrap">{b}</p>;
  });
}

function Editor({ value, cats, onClose }: { value: Partial<Row & { content: string }>; cats: Cat[]; onClose: (saved: boolean) => void }) {
  const lk = useLookups();
  const [v, setV] = useState(value);
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      const body = { title: v.title, summary: v.summary, content: v.content, articleType: v.articleType, categoryId: v.categoryId || undefined, projectId: v.projectId || undefined, tags: v.tags, status: v.status };
      if (v.id) await api.put(`/knowledge/articles/${v.id}`, body); else await api.post('/knowledge/articles', body);
      toast.success(v.status === 'PUBLISHED' ? 'Article published' : 'Article saved');
      onClose(true);
    } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
  };
  return (
    <Dialog open onOpenChange={() => onClose(false)} title={v.id ? 'Edit article' : 'New article'} className="max-w-3xl"
      footer={<><Button variant="outline" onClick={() => onClose(false)}>Cancel</Button><Button loading={busy} disabled={!v.title || !v.content} onClick={save}>Save</Button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Title" htmlFor="kb-title" className="sm:col-span-2"><Input id="kb-title" value={v.title ?? ''} onChange={(e) => setV({ ...v, title: e.target.value })} /></Field>
        <Field label="Type" htmlFor="kb-type"><NativeSelect id="kb-type" value={v.articleType} onChange={(e) => setV({ ...v, articleType: e.target.value })}>{['SOP', 'TROUBLESHOOTING', 'FAQ', 'RESOLUTION'].map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}</NativeSelect></Field>
        <Field label="Status" htmlFor="kb-status"><NativeSelect id="kb-status" value={v.status} onChange={(e) => setV({ ...v, status: e.target.value })}><option value="DRAFT">Draft</option><option value="PUBLISHED">Published</option><option value="ARCHIVED">Archived</option></NativeSelect></Field>
        <Field label="Category" htmlFor="kb-cat"><NativeSelect id="kb-cat" value={v.categoryId ?? ''} onChange={(e) => setV({ ...v, categoryId: e.target.value })}><option value="">None</option>{cats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</NativeSelect></Field>
        <Field label="Project" htmlFor="kb-proj" hint="Leave empty to share with all support teams"><NativeSelect id="kb-proj" value={v.projectId ?? ''} onChange={(e) => setV({ ...v, projectId: e.target.value })}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect></Field>
        <Field label="Summary" htmlFor="kb-sum" className="sm:col-span-2"><Input id="kb-sum" value={v.summary ?? ''} onChange={(e) => setV({ ...v, summary: e.target.value })} /></Field>
        <Field label="Content" htmlFor="kb-content" className="sm:col-span-2" hint="Use ## for headings, 1. for steps and - for bullet points"><Textarea id="kb-content" rows={12} className="font-mono text-[13px]" value={v.content ?? ''} onChange={(e) => setV({ ...v, content: e.target.value })} /></Field>
        <Field label="Tags" htmlFor="kb-tags" className="sm:col-span-2"><Input id="kb-tags" placeholder="vpn, network" value={v.tags ?? ''} onChange={(e) => setV({ ...v, tags: e.target.value })} /></Field>
      </div>
    </Dialog>
  );
}
