import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from '@/contexts/ThemeContext';

function Probe() {
  const { theme, toggle } = useTheme();
  return <button onClick={toggle}>{theme}</button>;
}

describe('theme', () => {
  beforeEach(() => { localStorage.clear(); document.documentElement.classList.remove('dark'); });

  it('defaults to light even when the OS prefers dark', () => {
    window.matchMedia = ((q: string) => ({ matches: q.includes('dark'), media: q, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
    render(<ThemeProvider><Probe /></ThemeProvider>);
    expect(screen.getByRole('button')).toHaveTextContent('LIGHT');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('persists an explicit dark preference', async () => {
    render(<ThemeProvider><Probe /></ThemeProvider>);
    await userEvent.click(screen.getByRole('button'));
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('suma.theme')).toBe('DARK');
  });
});
