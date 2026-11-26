import animate from 'tailwindcss-animate';

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"DM Sans"', 'system-ui', 'Segoe UI', 'sans-serif'],
        display: ['Sora', '"DM Sans"', 'system-ui', 'sans-serif'],
      },
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        surface: 'hsl(var(--surface))',
        primary: { DEFAULT: 'hsl(var(--primary))', foreground: 'hsl(var(--primary-foreground))' },
        violet: { DEFAULT: 'hsl(var(--violet))' },
        secondary: { DEFAULT: 'hsl(var(--secondary))', foreground: 'hsl(var(--secondary-foreground))' },
        muted: { DEFAULT: 'hsl(var(--muted))', foreground: 'hsl(var(--muted-foreground))' },
        accent: { DEFAULT: 'hsl(var(--accent))', foreground: 'hsl(var(--accent-foreground))' },
        destructive: { DEFAULT: 'hsl(var(--destructive))', foreground: 'hsl(var(--destructive-foreground))' },
        card: { DEFAULT: 'hsl(var(--card))', foreground: 'hsl(var(--card-foreground))' },
        popover: { DEFAULT: 'hsl(var(--popover))', foreground: 'hsl(var(--popover-foreground))' },
        navy: { DEFAULT: '#17142E', 900: '#0F0E1A', 800: '#17142E', 700: '#26224A', 600: '#3A3470' },
        ok: 'hsl(var(--ok))',
        warn: 'hsl(var(--warn))',
        breach: 'hsl(var(--breach))',
        idle: 'hsl(var(--idle))',
        sky: 'hsl(var(--sky))',
      },
      borderRadius: { '2xl': '22px', xl: '16px', lg: '12px', md: '10px', sm: '8px' },
      boxShadow: {
        glass: '0 1px 2px rgba(23,20,46,.04), 0 10px 30px -16px rgba(64,48,180,.22)',
        lift: '0 2px 6px rgba(23,20,46,.06), 0 24px 48px -20px rgba(64,48,180,.35)',
        glow: '0 10px 30px -10px rgba(91,76,240,.55)',
      },
      backgroundImage: {
        brand: 'linear-gradient(135deg, hsl(var(--brand-from)) 0%, hsl(var(--brand-to)) 100%)',
        'brand-soft': 'linear-gradient(135deg, hsl(var(--primary) / .12) 0%, hsl(var(--violet) / .12) 100%)',
      },
      keyframes: {
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        pulseRing: { '0%': { transform: 'scale(.8)', opacity: '.7' }, '100%': { transform: 'scale(2.2)', opacity: '0' } },
      },
      animation: { shimmer: 'shimmer 1.6s infinite', 'pulse-ring': 'pulseRing 1.8s ease-out infinite' },
    },
  },
  plugins: [animate],
};
