import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'motion/react';
import { useEffect, useId, type ReactNode } from 'react';
import { Avatar } from '@/components/common';
import { cn } from '@/utils/cn';

/** Circular gauge with a gradient sweep (0–100). */
export function RadialGauge({ value, size = 132, stroke = 12, label, sublabel, tone = 'brand' }: {
  value: number; size?: number; stroke?: number; label?: ReactNode; sublabel?: ReactNode; tone?: 'brand' | 'ok' | 'warn' | 'breach' | 'light';
}) {
  const id = useId();
  const reduce = useReducedMotion();
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, value));
  const colors = { brand: ['#5B4CF0', '#8B5CF6'], ok: ['#12B886', '#2E9BF0'], warn: ['#F5A524', '#F27A3D'], breach: ['#F0506E', '#F27A3D'], light: ['#FFFFFF', '#E9E5FF'] }[tone];
  return (
    <div className="relative inline-grid place-items-center" style={{ width: size, height: size }} role="img" aria-label={`${Math.round(pct)} percent`}>
      <svg width={size} height={size} className="-rotate-90">
        <defs><linearGradient id={id} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor={colors[0]} /><stop offset="1" stopColor={colors[1]} /></linearGradient></defs>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={tone === 'light' ? 'rgba(255,255,255,.22)' : 'hsl(var(--muted))'} strokeWidth={stroke} />
        <motion.circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`url(#${id})`} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} initial={{ strokeDashoffset: reduce ? c * (1 - pct / 100) : c }} animate={{ strokeDashoffset: c * (1 - pct / 100) }}
          transition={{ duration: 1.1, ease: [0.16, 1, 0.3, 1] }} />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">
        <div>{label}{sublabel && <div className="text-[11px] opacity-70">{sublabel}</div>}</div>
      </div>
    </div>
  );
}

/** Tiny trend line with gradient fill. */
export function Sparkline({ data, width = 160, height = 44, color = 'currentColor', className }: { data: number[]; width?: number; height?: number; color?: string; className?: string }) {
  const id = useId();
  if (data.length < 2) return <svg width={width} height={height} className={className} aria-hidden />;
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const step = width / (data.length - 1);
  const pts = data.map((v, i) => [i * step, height - 4 - ((v - min) / (max - min || 1)) * (height - 8)]);
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className={className} aria-hidden preserveAspectRatio="none">
      <defs><linearGradient id={id} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={color} stopOpacity=".35" /><stop offset="1" stopColor={color} stopOpacity="0" /></linearGradient></defs>
      <path d={`${line} L${width},${height} L0,${height} Z`} fill={`url(#${id})`} />
      <motion.path d={line} fill="none" stroke={color} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.8 }} />
      <circle cx={pts[pts.length - 1][0]} cy={pts[pts.length - 1][1]} r="3.5" fill={color} />
    </svg>
  );
}

export function AvatarStack({ names, max = 5, className }: { names: string[]; max?: number; className?: string }) {
  const shown = names.slice(0, max);
  return (
    <div className={cn('flex items-center', className)}>
      {shown.map((n, i) => <span key={n + i} title={n} className="-ml-2 first:ml-0"><Avatar name={n} /></span>)}
      {names.length > max && <span className="-ml-2 grid h-9 w-9 place-items-center rounded-full bg-secondary text-[11px] font-bold ring-2 ring-card">+{names.length - max}</span>}
    </div>
  );
}

export function LiveDot({ className }: { className?: string }) {
  return (
    <span className={cn('relative inline-flex h-2.5 w-2.5', className)} aria-hidden>
      <span className="absolute inline-flex h-full w-full animate-pulse-ring rounded-full bg-ok" />
      <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-ok" />
    </span>
  );
}

/** Big counter used in hero tiles. */
export function BigNumber({ value, className, format }: { value: number; className?: string; format?: (n: number) => string }) {
  const reduce = useReducedMotion();
  const mv = useMotionValue(reduce ? value : 0);
  const text = useTransform(mv, (v) => (format ? format(v) : Math.round(v).toLocaleString()));
  useEffect(() => {
    if (reduce) { mv.set(value); return; }
    const a = animate(mv, value, { duration: 1, ease: [0.16, 1, 0.3, 1] });
    return () => a.stop();
  }, [value, mv, reduce]);
  return <motion.span className={cn('display-num', className)}>{text}</motion.span>;
}

/**
 * Day clock: outer ring = signed-in time against a target day, inner ring = break time against the daily break limit.
 * Idle is shown as its own small arc so it is never confused with breaks.
 */
export function ShiftRing({ workedSeconds, targetSeconds, breakSeconds, breakLimitSeconds, idleSeconds, center, size = 220 }: {
  workedSeconds: number; targetSeconds: number; breakSeconds: number; breakLimitSeconds?: number; idleSeconds: number; center: ReactNode; size?: number;
}) {
  const id = useId();
  const reduce = useReducedMotion();
  const rings = [
    { r: size / 2 - 12, w: 14, pct: Math.min(1, workedSeconds / Math.max(1, targetSeconds)), stroke: `url(#${id}w)` },
    { r: size / 2 - 34, w: 10, pct: Math.min(1, breakSeconds / Math.max(1, breakLimitSeconds ?? 3600)), stroke: breakLimitSeconds && breakSeconds > breakLimitSeconds ? '#F0506E' : '#F5A524' },
    { r: size / 2 - 52, w: 8, pct: Math.min(1, idleSeconds / 3600), stroke: '#A78BFA' },
  ];
  return (
    <div className="relative inline-grid place-items-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" aria-hidden>
        <defs><linearGradient id={`${id}w`} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#5B4CF0" /><stop offset="1" stopColor="#8B5CF6" /></linearGradient></defs>
        {rings.map((g, i) => {
          const c = 2 * Math.PI * g.r;
          return (
            <g key={i}>
              <circle cx={size / 2} cy={size / 2} r={g.r} fill="none" stroke="hsl(var(--muted))" strokeWidth={g.w} />
              <motion.circle cx={size / 2} cy={size / 2} r={g.r} fill="none" stroke={g.stroke} strokeWidth={g.w} strokeLinecap="round" strokeDasharray={c}
                initial={{ strokeDashoffset: reduce ? c * (1 - g.pct) : c }} animate={{ strokeDashoffset: c * (1 - g.pct) }} transition={{ duration: 1.2, delay: i * 0.12, ease: [0.16, 1, 0.3, 1] }} />
            </g>
          );
        })}
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">{center}</div>
    </div>
  );
}

/** Legend for charts with two or more series: colour swatch + name, so identity never relies on colour alone. */
export function ChartLegend({ items }: { items: [color: string, label: string][] }) {
  return (
    <ul className="flex flex-wrap gap-3 text-xs text-muted-foreground" aria-label="Legend">
      {items.map(([c, l]) => <li key={l} className="flex items-center gap-1.5"><span aria-hidden className="h-2.5 w-2.5 rounded-full" style={{ background: c }} />{l}</li>)}
    </ul>
  );
}
