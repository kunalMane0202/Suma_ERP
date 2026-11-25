import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'motion/react';
import { AlertTriangle, ChevronLeft, ChevronRight, Inbox } from 'lucide-react';
import { useEffect, type ReactNode } from 'react';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/utils/cn';
import { titleCase } from '@/utils/format';
import type { Priority, SlaInfo, TicketStatus } from '@/types/api';

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-[22px] font-semibold leading-tight text-foreground">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

/** Number that counts up once when it first renders (respects reduced motion). */
export function AnimatedNumber({ value, format }: { value: number; format?: (n: number) => string }) {
  const reduce = useReducedMotion();
  const mv = useMotionValue(reduce ? value : 0);
  const text = useTransform(mv, (v) => (format ? format(v) : Math.round(v).toLocaleString()));
  useEffect(() => {
    if (reduce) { mv.set(value); return; }
    const c = animate(mv, value, { duration: 0.8, ease: [0.16, 1, 0.3, 1] });
    return () => c.stop();
  }, [value, mv, reduce]);
  return <motion.span className="num">{text}</motion.span>;
}

export function Stat({ label, value, hint, tone = 'default', icon, format, index = 0 }: {
  label: string; value: number; hint?: ReactNode; tone?: 'default' | 'ok' | 'warn' | 'breach' | 'idle' | 'primary'; icon?: ReactNode;
  format?: (n: number) => string; index?: number;
}) {
  const toneClass = { default: 'text-foreground', ok: 'text-ok', warn: 'text-warn', breach: 'text-breach', idle: 'text-idle', primary: 'text-primary' }[tone];
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.035, duration: 0.35, ease: 'easeOut' }}
      className="glass min-w-0 rounded-lg px-4 py-3.5">
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span className="truncate">{label}</span>
        {icon && <span className="text-muted-foreground/70 [&_svg]:size-4">{icon}</span>}
      </div>
      <div className={cn('mt-1.5 text-2xl font-semibold leading-none tracking-tight', toneClass)}>
        <AnimatedNumber value={value} format={format} />
      </div>
      {hint && <div className="mt-1.5 text-xs text-muted-foreground">{hint}</div>}
    </motion.div>
  );
}

export function EmptyState({ title, description, action, icon }: { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-accent p-3 text-primary [&_svg]:size-5">{icon ?? <Inbox />}</div>
      <p className="font-medium">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border border-breach/30 bg-breach/5 p-4 text-sm">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-breach" />
      <div className="flex-1"><p className="font-medium text-breach">Could not load this data</p><p className="text-muted-foreground">{message}</p></div>
      {onRetry && <Button size="sm" variant="outline" onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function Pagination({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return <p className="px-4 py-3 text-xs text-muted-foreground">{total} record{total === 1 ? '' : 's'}</p>;
  return (
    <div className="flex items-center justify-between gap-2 border-t px-4 py-2.5 text-xs text-muted-foreground">
      <span>{total} records · page {page + 1} of {totalPages}</span>
      <div className="flex gap-1">
        <Button variant="outline" size="icon" className="h-7 w-7" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page"><ChevronLeft /></Button>
        <Button variant="outline" size="icon" className="h-7 w-7" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page"><ChevronRight /></Button>
      </div>
    </div>
  );
}

export function Can({ permission, any, children, fallback = null }: { permission?: string; any?: string[]; children: ReactNode; fallback?: ReactNode }) {
  const { can, canAny } = useAuth();
  const ok = permission ? can(permission) : any ? canAny(...any) : true;
  return <>{ok ? children : fallback}</>;
}

const statusTone: Record<TicketStatus, BadgeTone> = {
  NEW: 'blue', ASSIGNED: 'violet', IN_PROGRESS: 'blue', PENDING: 'amber', RESOLVED: 'green', CLOSED: 'neutral', REOPENED: 'red',
};
export function StatusBadge({ status }: { status: TicketStatus }) {
  return <Badge tone={statusTone[status]}>{titleCase(status)}</Badge>;
}

const priorityStyle: Record<Priority, string> = {
  CRITICAL: 'bg-breach', HIGH: 'bg-[#E0782F]', MEDIUM: 'bg-warn', LOW: 'bg-ok',
};
export function PriorityTag({ priority }: { priority: Priority }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium">
      <span className={cn('h-2 w-2 rounded-full', priorityStyle[priority])} aria-hidden />{titleCase(priority)}
    </span>
  );
}

export function SlaBadge({ sla }: { sla: SlaInfo }) {
  const map: Record<SlaInfo['state'], [BadgeTone, string]> = {
    NO_SLA: ['neutral', 'No SLA'], ON_TRACK: ['green', 'On track'], AT_RISK: ['amber', 'At risk'], BREACHED: ['red', 'Breached'],
    MET: ['green', 'Met'], PAUSED: ['neutral', 'Paused'],
  };
  const [tone, label] = map[sla.state];
  let extra = '';
  if (sla.resolutionRemainingSeconds != null && sla.state !== 'BREACHED') {
    const h = Math.floor(sla.resolutionRemainingSeconds / 3600);
    const m = Math.floor((sla.resolutionRemainingSeconds % 3600) / 60);
    extra = h > 0 ? ` · ${h}h ${m}m left` : ` · ${m}m left`;
  }
  return <Badge tone={tone}>{label}{extra}</Badge>;
}

export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
  return (
    <span className={cn('inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary/80 to-navy-600 text-[11px] font-semibold text-white', className)} aria-hidden>
      {initials}
    </span>
  );
}
