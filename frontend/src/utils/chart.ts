/** Chart palette: one hue per meaning, consistent across every chart (dataviz skill guidance). */
export const CHART = {
  primary: '#5B4CF0',
  violet: '#8B5CF6',
  primarySoft: '#B3A9FA',
  ok: '#12B886',
  warn: '#F5A524',
  breach: '#F0506E',
  idle: '#A78BFA',
  sky: '#2E9BF0',
  orange: '#F27A3D',
  navy: '#17142E',
  grid: 'hsl(var(--border))',
  axis: 'hsl(var(--muted-foreground))',
};

export const PRIORITY_COLORS: Record<string, string> = { CRITICAL: CHART.breach, HIGH: CHART.orange, MEDIUM: CHART.warn, LOW: CHART.ok };

export const tooltipStyle = {
  contentStyle: {
    background: 'hsl(var(--popover))', border: 'none', borderRadius: 14, fontSize: 12, padding: '8px 12px',
    boxShadow: '0 16px 40px -16px rgba(64,48,180,.45), 0 0 0 1px hsl(var(--border))', color: 'hsl(var(--popover-foreground))',
  },
  labelStyle: { color: 'hsl(var(--muted-foreground))', marginBottom: 4, fontWeight: 600 },
  cursor: { fill: 'hsl(var(--primary) / .06)' },
};
