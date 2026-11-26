import { motion } from 'motion/react';
import { Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '@/components/ui/card';
import { BigNumber } from '@/components/viz';
import { cn } from '@/utils/cn';

/** Pill toggle (e.g. Cards / Table). */
export function SegmentedControl<T extends string>({ value, onChange, options, label }: { value: T; onChange: (v: T) => void; options: { value: T; label: ReactNode }[]; label: string }) {
  return (
    <div className="inline-flex rounded-full bg-muted p-1" role="group" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" onClick={() => onChange(o.value)} aria-pressed={value === o.value}
          className={cn('relative rounded-full px-3 py-1 text-xs font-semibold transition-colors', value === o.value ? 'text-foreground' : 'text-muted-foreground hover:text-foreground')}>
          {value === o.value && <motion.span layoutId={`seg-${label}`} className="absolute inset-0 rounded-full bg-card shadow-glass" transition={{ type: 'spring', stiffness: 400, damping: 34 }} />}
          <span className="relative">{o.label}</span>
        </button>
      ))}
    </div>
  );
}

/** Rounded filter row used on list pages. */
export function FilterBar({ children, className, right }: { children: ReactNode; className?: string; right?: ReactNode }) {
  return (
    <div className={cn('glass mb-4 flex flex-wrap items-center gap-2 p-2.5', className)}>
      {children}
      {right && <><div className="flex-1" />{right}</>}
    </div>
  );
}

export function SearchField({ value, onChange, placeholder, label, className }: { value: string; onChange: (v: string) => void; placeholder: string; label: string; className?: string }) {
  return (
    <div className={cn('relative w-full sm:w-72', className)}>
      <Search className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-muted-foreground" />
      <input aria-label={label} placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)}
        className="h-10 w-full rounded-full bg-muted pl-10 pr-4 text-sm outline-none transition placeholder:text-muted-foreground hover:bg-secondary focus-visible:bg-card focus-visible:ring-2 focus-visible:ring-ring/60 focus-visible:ring-offset-0" />
    </div>
  );
}

export interface HeroMetric { label: string; value: number; format?: (n: number) => string; tone?: string }

/** Page-top banner with headline numbers. */
export function HeroBanner({ icon: Icon, title, subtitle, metrics, action, tone = 'brand' }: {
  icon?: LucideIcon; title?: ReactNode; subtitle?: ReactNode; metrics: HeroMetric[]; action?: ReactNode; tone?: 'brand' | 'ink' | 'tint';
}) {
  const light = tone !== 'tint';
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ type: 'spring', stiffness: 220, damping: 24 }} className="mb-5">
      <Card tone={tone} className="relative overflow-hidden">
        {light && <><div className="pointer-events-none absolute -right-16 -top-20 h-60 w-60 rounded-full bg-white/10 blur-2xl" />
          <div className="pointer-events-none absolute -bottom-24 left-1/3 h-56 w-56 rounded-full bg-[#F0506E]/20 blur-3xl" /></>}
        <div className="relative flex flex-col gap-5 p-6 lg:flex-row lg:items-center">
          {(title || Icon) && (
            <div className="flex min-w-0 items-center gap-4 lg:w-72">
              {Icon && <span className={cn('grid h-12 w-12 shrink-0 place-items-center rounded-2xl', light ? 'bg-white/20 text-white' : 'bg-brand text-white shadow-glow')}><Icon className="h-5 w-5" /></span>}
              <div className="min-w-0">
                {title && <p className="font-display text-lg font-semibold leading-tight">{title}</p>}
                {subtitle && <p className={cn('mt-0.5 text-sm', light ? 'text-white/75' : 'text-muted-foreground')}>{subtitle}</p>}
              </div>
            </div>
          )}
          <div className="grid flex-1 grid-cols-2 gap-3 sm:grid-cols-4">
            {metrics.map((m) => (
              <div key={m.label} className={cn('rounded-2xl px-4 py-3', light ? 'bg-white/15 backdrop-blur' : 'bg-card/80')}>
                <p className={cn('text-xs', light ? 'text-white/75' : 'text-muted-foreground')}>{m.label}</p>
                <BigNumber value={m.value} format={m.format} className={cn('text-[28px] leading-tight', m.tone)} />
              </div>
            ))}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </div>
      </Card>
    </motion.div>
  );
}

const ICON_TONES: Record<string, string> = {
  brand: 'bg-brand text-white shadow-glow', mint: 'bg-gradient-to-br from-[#12B886] to-[#2E9BF0] text-white', amber: 'bg-gradient-to-br from-[#F5A524] to-[#F0506E] text-white',
  sky: 'bg-gradient-to-br from-[#2E9BF0] to-[#5B4CF0] text-white', coral: 'bg-gradient-to-br from-[#F0506E] to-[#A78BFA] text-white', soft: 'bg-primary/10 text-primary',
  ink: 'bg-navy text-white',
};

export function IconTile({ icon: Icon, tone = 'soft', className }: { icon: LucideIcon; tone?: keyof typeof ICON_TONES | string; className?: string }) {
  return <span className={cn('grid h-10 w-10 shrink-0 place-items-center rounded-xl', ICON_TONES[tone] ?? ICON_TONES.soft, className)}><Icon className="h-[18px] w-[18px]" /></span>;
}

export function KeyValue({ items, className }: { items: [ReactNode, ReactNode][]; className?: string }) {
  return (
    <dl className={cn('divide-y divide-border/60 rounded-xl bg-muted/50 px-4', className)}>
      {items.map(([k, v], i) => (
        <div key={i} className="flex items-start justify-between gap-4 py-2.5 text-sm">
          <dt className="shrink-0 text-muted-foreground">{k}</dt><dd className="min-w-0 text-right font-medium">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Stagger container + item for card grids. */
export const gridParent = { hidden: {}, show: { transition: { staggerChildren: 0.04 } } };
export const gridItem = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { type: 'spring' as const, stiffness: 260, damping: 24 } } };

export function CardGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <motion.div className={cn('grid gap-4 sm:grid-cols-2 xl:grid-cols-3', className)} variants={gridParent} initial="hidden" animate="show">{children}</motion.div>;
}

/** Card in a grid. With `to`, the whole card is a real link (keyboard, Cmd/Ctrl+click); inner links and buttons stay clickable. */
export function GridCard({ children, className, to, label }: { children: ReactNode; className?: string; to?: string; label?: string }) {
  return (
    <motion.div variants={gridItem} whileHover={{ y: -4 }}
      className={cn('glass relative flex flex-col p-5 transition-shadow hover:shadow-lift', to && 'focus-within:ring-2 focus-within:ring-ring', className)}>
      {to ? (
        <>
          <Link to={to} aria-label={label} className="absolute inset-0 z-0 rounded-[inherit] focus-visible:outline-none" />
          <div className="pointer-events-none relative z-[1] flex flex-1 flex-col [&_a]:pointer-events-auto [&_button]:pointer-events-auto">{children}</div>
        </>
      ) : children}
    </motion.div>
  );
}

/** Feed row with a coloured dot/icon (timelines, logs). */
export function FeedItem({ icon: Icon, tone = 'bg-primary/10 text-primary', title, meta, children, right }: {
  icon: LucideIcon; tone?: string; title: ReactNode; meta?: ReactNode; children?: ReactNode; right?: ReactNode;
}) {
  return (
    <li className="relative flex gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-accent/60">
      <span className={cn('grid h-9 w-9 shrink-0 place-items-center rounded-xl', tone)}><Icon className="h-4 w-4" /></span>
      <div className="min-w-0 flex-1">
        <div className="text-sm">{title}</div>
        {children}
        {meta && <div className="mt-0.5 text-xs text-muted-foreground">{meta}</div>}
      </div>
      {right && <div className="shrink-0">{right}</div>}
    </li>
  );
}
