import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { LoginPage } from '@/modules/auth/LoginPage';
import { ThemeProvider } from '@/contexts/ThemeContext';

const calls: string[][] = [];
let impl: (u: string, p: string) => Promise<unknown> = async () => ({ themePreference: 'LIGHT' });
const login = (u: string, p: string) => { calls.push([u, p]); return impl(u, p); };
vi.mock('@/contexts/AuthContext', () => ({ useAuth: () => ({ login, user: null, ready: true }) }));

function renderPage() {
  return render(<ThemeProvider><MemoryRouter><LoginPage /></MemoryRouter></ThemeProvider>);
}

describe('LoginPage', () => {
  beforeEach(() => { calls.length = 0; });

  it('validates required fields before calling the API', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Enter your username or email')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('shows the server error message on failure', async () => {
    impl = () => Promise.reject(Object.assign(new Error('401'), { response: { data: { message: 'Invalid username or password' } } }));
    renderPage();
    await userEvent.type(screen.getByLabelText('Username or email'), 'neha');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Invalid username or password'));
    expect(calls[0]).toEqual(['neha', 'wrong']);
  });

  it('explains what attendance data is recorded', () => {
    renderPage();
    expect(screen.getByText(/records your sign-in and sign-out times/i)).toBeInTheDocument();
  });
});
