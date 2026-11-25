import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

type Theme = 'LIGHT' | 'DARK';
const KEY = 'suma.theme';

interface ThemeState { theme: Theme; setTheme: (t: Theme) => void; toggle: () => Theme }
const ThemeContext = createContext<ThemeState | null>(null);

function stored(): Theme {
  try {
    return localStorage.getItem(KEY) === 'DARK' ? 'DARK' : 'LIGHT';
  } catch {
    return 'LIGHT';
  }
}

/** Light is the default for every new user. The operating-system colour scheme is deliberately ignored. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(stored);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'DARK');
    document.documentElement.style.colorScheme = theme === 'DARK' ? 'dark' : 'light';
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* storage unavailable: preference lasts for this tab only */
    }
  }, [theme]);

  const setTheme = useCallback((t: Theme) => setThemeState(t), []);
  const toggle = useCallback(() => {
    const next: Theme = theme === 'DARK' ? 'LIGHT' : 'DARK';
    setThemeState(next);
    return next;
  }, [theme]);

  return <ThemeContext.Provider value={{ theme, setTheme, toggle }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}
