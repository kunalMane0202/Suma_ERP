import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'motion/react';
import { AlertTriangle, ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, type ReactNode } from 'react';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/utils/cn';
import { titleCase } from '@/utils/format';
import type { Priority, SlaInfo, TicketStatus } from '@/types/api';

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-7 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-[28px] font-semibold leading-[1.15] text-foreground sm:text-[32px]">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-[14px] text-muted-foreground">{description}</p>}
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

const toneChip: Record<string, string> = {
  default: 'bg-secondary text-foreground/70', ok: 'bg-ok/15 text-ok', warn: 'bg-warn/15 text-warn', breach: 'bg-breach/15 text-breach',
  idle: 'bg-idle/15 text-idle', primary: 'bg-primary/15 text-primary',
};

export function Stat({ label, value, hint, tone = 'default', icon, format, index = 0, className }: {
  label: string; value: number; hint?: ReactNode; tone?: 'default' | 'ok' | 'warn' | 'breach' | 'idle' | 'primary'; icon?: ReactNode;
  format?: (n: number) => string; index?: number; className?: string;
}) {
  const toneClass = { default: 'text-foreground', ok: 'text-ok', warn: 'text-warn', breach: 'text-breach', idle: 'text-idle', primary: 'text-primary' }[tone];
  return (
    <motion.div
      initial={{ opacity: 0, y: 12, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ delay: index * 0.04, type: 'spring', stiffness: 260, damping: 24 }}
      whileHover={{ y: -3 }}
      className={cn('glass min-w-0 rounded-2xl px-5 py-4', className)}>
      <div className="flex items-start justify-between gap-2">
        <span className="text-[13px] font-medium text-muted-foreground">{label}</span>
        {icon && <span className={cn('grid h-8 w-8 shrink-0 place-items-center rounded-xl [&_svg]:size-4', toneChip[tone])}>{icon}</span>}
      </div>
      <div className={cn('display-num mt-2 text-[30px] leading-none', toneClass)}>
        <AnimatedNumber value={value} format={format} />
      </div>
      {hint && <div className="mt-2 text-xs text-muted-foreground">{hint}</div>}
    </motion.div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <svg width="132" height="96" viewBox="0 0 132 96" aria-hidden className="mb-4">
        <defs><linearGradient id="es" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="hsl(var(--primary))" /><stop offset="1" stopColor="hsl(var(--violet))" /></linearGradient></defs>
        <rect x="10" y="14" width="52" height="34" rx="10" fill="hsl(var(--primary) / .12)" />
        <rect x="70" y="14" width="52" height="34" rx="17" fill="hsl(var(--violet) / .14)" />
        <rect x="10" y="54" width="112" height="30" rx="10" fill="url(#es)" opacity=".9" />
        <rect x="22" y="65" width="40" height="8" rx="4" fill="white" opacity=".85" />
        <circle cx="105" cy="69" r="6" fill="white" opacity=".6" />
        <rect x="20" y="26" width="24" height="6" rx="3" fill="hsl(var(--primary) / .45)" />
      </svg>
      <p className="font-display text-[15px] font-semibold">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex items-start gap-3 rounded-2xl bg-breach/10 p-4 text-sm ring-1 ring-breach/20">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-breach" />
      <div className="flex-1"><p className="font-medium text-breach">Could not load this data</p><p className="text-muted-foreground">{message}</p></div>
      {onRetry && <Button size="sm" variant="outline" onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function Pagination({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return <p className="px-5 py-3 text-xs text-muted-foreground">{total} record{total === 1 ? '' : 's'}</p>;
  return (
    <div className="flex items-center justify-between gap-2 px-5 py-3 text-xs text-muted-foreground">
      <span>{total} records · page {page + 1} of {totalPages}</span>
      <div className="flex gap-1">
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page"><ChevronLeft /></Button>
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page"><ChevronRight /></Button>
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
  CRITICAL: 'bg-breach shadow-[0_0_0_3px_hsl(var(--breach)/.2)]', HIGH: 'bg-[#F27A3D]', MEDIUM: 'bg-warn', LOW: 'bg-ok',
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

const AVATAR_GRADIENTS = [
  'from-[#5B4CF0] to-[#8B5CF6]', 'from-[#12B886] to-[#2E9BF0]', 'from-[#F5A524] to-[#F0506E]',
  'from-[#2E9BF0] to-[#5B4CF0]', 'from-[#F0506E] to-[#A78BFA]', 'from-[#0EA5A4] to-[#12B886]',
];

export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
  let h = 0;
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return (
    <span className={cn('inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-[11px] font-bold text-white ring-2 ring-card', AVATAR_GRADIENTS[h % AVATAR_GRADIENTS.length], className)} aria-hidden>
      {initials}
    </span>
  );
}
