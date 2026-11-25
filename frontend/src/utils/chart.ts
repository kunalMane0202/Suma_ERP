export const CHART = {
  primary: '#1D5BD8',
  primarySoft: '#8FB0F2',
  ok: '#1A8A5E',
  warn: '#C98A0B',
  breach: '#C93C3C',
  idle: '#8A5CC7',
  navy: '#0F2240',
  grid: 'hsl(var(--border))',
  axis: 'hsl(var(--muted-foreground))',
};

export const tooltipStyle = {
  contentStyle: {
    background: 'hsl(var(--popover))', border: '1px solid hsl(var(--border))', borderRadius: 8, fontSize: 12,
    boxShadow: '0 8px 24px -12px rgba(15,34,64,.3)', color: 'hsl(var(--popover-foreground))',
  },
  labelStyle: { color: 'hsl(var(--muted-foreground))', marginBottom: 4 },
  cursor: { fill: 'hsl(var(--accent))', opacity: 0.6 },
};
