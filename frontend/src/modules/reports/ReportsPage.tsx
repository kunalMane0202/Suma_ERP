import { Download, FileSpreadsheet } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect } from '@/components/ui/input';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { TableSkeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState, PageHeader } from '@/components/common';
import { useApi } from '@/hooks/useApi';
import { useLookups } from '@/hooks/useLookups';
import { useAuth } from '@/contexts/AuthContext';
import { download, errorMessage } from '@/services/api';
import { CHART, tooltipStyle } from '@/utils/chart';
import { dateTime, isoDate } from '@/utils/format';

const TYPES: { key: string; label: string; description: string; chart?: { x: string; bars: [string, string][] } }[] = [
  { key: 'productivity', label: 'Employee productivity', description: 'Assigned, resolved and breached tickets per engineer', chart: { x: 'employee', bars: [['resolved', CHART.ok], ['open', CHART.primary], ['breached', CHART.breach]] } },
  { key: 'sla', label: 'SLA compliance', description: 'Response and resolution compliance by project and priority' },
  { key: 'pending', label: 'Pending tickets', description: 'Every open ticket with its age and due time' },
  { key: 'ageing', label: 'Ticket ageing', description: 'Open tickets bucketed by age', chart: { x: 'project', bars: [['lt24h', CHART.ok], ['d1to3', CHART.primarySoft], ['d3to7', CHART.warn], ['d7to14', '#E0782F'], ['gt14d', CHART.breach]] } },
  { key: 'daily', label: 'Daily report', description: 'Today, ticket movement' },
  { key: 'weekly', label: 'Weekly report', description: 'Last 7 days, by day', chart: { x: 'date', bars: [['received', CHART.primary], ['resolved', CHART.ok], ['breached', CHART.breach]] } },
  { key: 'monthly', label: 'Monthly report', description: 'Last 30 days, by day', chart: { x: 'date', bars: [['received', CHART.primary], ['resolved', CHART.ok], ['breached', CHART.breach]] } },
  { key: 'client', label: 'Client report', description: 'Volumes and breaches per client', chart: { x: 'client', bars: [['received', CHART.primary], ['resolved', CHART.ok], ['breached', CHART.breach]] } },
  { key: 'project', label: 'Project report', description: 'Volumes and breaches per project', chart: { x: 'project', bars: [['received', CHART.primary], ['resolved', CHART.ok], ['breached', CHART.breach]] } },
  { key: 'team', label: 'Team report', description: 'Volumes and breaches per team', chart: { x: 'team', bars: [['received', CHART.primary], ['resolved', CHART.ok], ['breached', CHART.breach]] } },
];

interface Report { type: string; title: string; from: string; to: string; columns: string[]; rows: Record<string, unknown>[] }
const label = (c: string) => c.replace(/([A-Z])/g, ' $1').replace(/^lt24h$/, '< 24h').replace(/^d(\d+)to(\d+)$/, '$1–$2 days').replace(/^gt14d$/, '> 14 days').replace(/^./, (x) => x.toUpperCase());

export default function ReportsPage() {
  const { can, user } = useAuth();
  const lk = useLookups();
  const [type, setType] = useState('productivity');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState(isoDate(new Date()));
  const [projectId, setProjectId] = useState('');
  const def = TYPES.find((t) => t.key === type)!;
  const canOps = can('REPORT_VIEW');
  const { data, error, loading, reload } = useApi<Report>(canOps ? `/reports/${type}` : null, { from: from || undefined, to, projectId: projectId || undefined });
  const exp = async (format: string) => {
    try { await download(`/reports/${type}/export`, { format, from: from || undefined, to, projectId: projectId || undefined }); } catch (e) { toast.error(errorMessage(e)); }
  };
  const fmt = (v: unknown) => (v == null ? '—' : typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(v) ? dateTime(v) : typeof v === 'boolean' ? (v ? 'Yes' : 'No') : String(v));

  return (
    <>
      <PageHeader title="Reports & analytics" description="Operational reports computed from live ticket data. Attendance reports live under Team attendance." />
      {(can('ATTENDANCE_REPORT_VIEW') || user?.projectLead) && (
        <Card className="mb-4"><CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
          <div><p className="font-medium">Attendance reports</p><p className="text-sm text-muted-foreground">Daily and monthly attendance with Excel, CSV and PDF export, plus Project Lead deliveries.</p></div>
          <Button asChild variant="outline"><Link to="/attendance/team">Open attendance reports</Link></Button>
        </CardContent></Card>
      )}
      {canOps && (
        <div className="grid gap-4 lg:grid-cols-[16rem_1fr]">
          <Card className="h-fit p-2">
            <nav aria-label="Report types"><ul className="space-y-0.5">
              {TYPES.map((t) => (
                <li key={t.key}><button onClick={() => setType(t.key)} className={`w-full rounded-md px-3 py-2 text-left text-sm ${type === t.key ? 'bg-primary text-primary-foreground' : 'hover:bg-accent'}`}>{t.label}</button></li>
              ))}
            </ul></nav>
          </Card>
          <div className="min-w-0 space-y-4">
            <Card>
              <CardHeader className="flex-wrap">
                <div><CardTitle>{def.label}</CardTitle><CardDescription>{def.description}{data ? ` · ${data.from} to ${data.to}` : ''}</CardDescription></div>
                <div className="flex flex-wrap items-center gap-2">
                  <Input type="date" aria-label="From" className="h-8 w-40 text-xs" value={from} onChange={(e) => setFrom(e.target.value)} />
                  <Input type="date" aria-label="To" className="h-8 w-40 text-xs" value={to} onChange={(e) => setTo(e.target.value)} />
                  {lk.projects.length > 0 && <NativeSelect aria-label="Project" className="h-8 w-44 text-xs" value={projectId} onChange={(e) => setProjectId(e.target.value)}><option value="">All projects</option>{lk.projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</NativeSelect>}
                  {can('REPORT_EXPORT') && <><Button size="sm" variant="outline" onClick={() => exp('xlsx')}><FileSpreadsheet /> Excel</Button><Button size="sm" variant="outline" onClick={() => exp('csv')}><Download /> CSV</Button></>}
                </div>
              </CardHeader>
              {def.chart && data && data.rows.length > 0 && (
                <CardContent className="h-[260px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={data.rows.slice(0, 31)} margin={{ left: -16, right: 8 }}>
                      <CartesianGrid stroke={CHART.grid} strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey={def.chart.x} tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} interval="preserveStartEnd" tickFormatter={(v: string) => (v.length > 14 ? v.slice(0, 13) + '…' : v)} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: CHART.axis }} tickLine={false} axisLine={false} />
                      <RTooltip {...tooltipStyle} />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      {def.chart.bars.map(([k, c]) => <Bar key={k} dataKey={k} name={label(k)} fill={c} radius={[3, 3, 0, 0]} stackId={type === 'ageing' ? 'a' : undefined} />)}
                    </BarChart>
                  </ResponsiveContainer>
                </CardContent>
              )}
            </Card>
            <Card>
              {error && <div className="p-4"><ErrorState message={error} onRetry={reload} /></div>}
              {loading && !data ? <TableSkeleton /> : data && (data.rows.length === 0 ? <EmptyState title="No data for this range" /> : (
                <Table>
                  <THead><TR>{data.columns.map((c) => <TH key={c}>{label(c)}</TH>)}</TR></THead>
                  <TBody>{data.rows.map((r, i) => <TR key={i}>{data.columns.map((c) => <TD key={c} className={typeof r[c] === 'number' ? 'num' : ''}>{fmt(r[c])}</TD>)}</TR>)}</TBody>
                </Table>
              ))}
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
