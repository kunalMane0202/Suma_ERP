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

/**
 * Ticket-flow series (received / resolved / breached, also success / failed).
 * Checked with the dataviz validator in light and dark: distinguishable with red-green colour blindness
 * (the old green/red pair was not) and >= 3:1 against both chart surfaces.
 */
export const SERIES = { received: '#5B4CF0', resolved: '#0E9F8E', breached: '#E8590C' };

/**
 * Ordered categories (priority, ticket age) use one coral hue with stepped lightness, so the order is
 * readable from the colour itself. Dark mode has its own validated steps (lighter = more severe).
 */
const PRIORITY_LIGHT: Record<string, string> = { LOW: '#ee939b', MEDIUM: '#de6674', HIGH: '#c73751', CRITICAL: '#9a1c37' };
const PRIORITY_DARK: Record<string, string> = { LOW: '#8b333f', MEDIUM: '#c34658', HIGH: '#ea6878', CRITICAL: '#ff9da5' };
const AGEING_LIGHT = ['#ed9aa0', '#e4737f', '#d54e62', '#bd2c49', '#961735'];
const AGEING_DARK = ['#84343f', '#b13e4f', '#d85164', '#f37986', '#ffa6ad'];

export const priorityColors = (dark: boolean) => (dark ? PRIORITY_DARK : PRIORITY_LIGHT);
export const ageingColors = (dark: boolean) => (dark ? AGEING_DARK : AGEING_LIGHT);

export const tooltipStyle = {
  contentStyle: {
    background: 'hsl(var(--popover))', border: 'none', borderRadius: 14, fontSize: 12, padding: '8px 12px',
    boxShadow: '0 16px 40px -16px rgba(64,48,180,.45), 0 0 0 1px hsl(var(--border))', color: 'hsl(var(--popover-foreground))',
  },
  labelStyle: { color: 'hsl(var(--muted-foreground))', marginBottom: 4, fontWeight: 600 },
  cursor: { fill: 'hsl(var(--primary) / .06)' },
};
