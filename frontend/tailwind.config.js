import animate from 'tailwindcss-animate';
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['"IBM Plex Sans"', 'system-ui', 'Segoe UI', 'sans-serif'] },
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        surface: 'hsl(var(--surface))',
        primary: { DEFAULT: 'hsl(var(--primary))', foreground: 'hsl(var(--primary-foreground))' },
        secondary: { DEFAULT: 'hsl(var(--secondary))', foreground: 'hsl(var(--secondary-foreground))' },
        muted: { DEFAULT: 'hsl(var(--muted))', foreground: 'hsl(var(--muted-foreground))' },
        accent: { DEFAULT: 'hsl(var(--accent))', foreground: 'hsl(var(--accent-foreground))' },
        destructive: { DEFAULT: 'hsl(var(--destructive))', foreground: 'hsl(var(--destructive-foreground))' },
        card: { DEFAULT: 'hsl(var(--card))', foreground: 'hsl(var(--card-foreground))' },
        popover: { DEFAULT: 'hsl(var(--popover))', foreground: 'hsl(var(--popover-foreground))' },
        navy: { DEFAULT: '#0F2240', 900: '#0A1830', 800: '#0F2240', 700: '#18315A', 600: '#234574' },
        ok: 'hsl(var(--ok))',
        warn: 'hsl(var(--warn))',
        breach: 'hsl(var(--breach))',
        idle: 'hsl(var(--idle))',
      },
      borderRadius: { lg: '10px', md: '8px', sm: '6px' },
      boxShadow: {
        glass: '0 1px 0 rgba(255,255,255,0.7) inset, 0 1px 2px rgba(15,34,64,0.06), 0 8px 24px -12px rgba(15,34,64,0.18)',
        lift: '0 2px 4px rgba(15,34,64,0.06), 0 16px 40px -16px rgba(15,34,64,0.28)',
      },
    },
  },
  plugins: [animate],
};
