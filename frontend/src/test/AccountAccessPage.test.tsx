import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { ForgotPasswordPage, PasswordActionPage, RegistrationRequestPage } from '@/modules/auth/AccountAccessPage';

const { apiPost } = vi.hoisted(() => ({ apiPost: vi.fn() }));
vi.mock('@/services/api', () => ({
  api: { post: apiPost },
  errorMessage: () => 'Request failed',
}));

describe('account access pages', () => {
  beforeEach(() => { apiPost.mockReset().mockResolvedValue({}); });

  it('shows field validation errors without an uncaught validation exception', async () => {
    render(<MemoryRouter><RegistrationRequestPage /></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: 'Submit request' }));
    expect(await screen.findByText('Enter your full name')).toBeInTheDocument();
    expect(apiPost).not.toHaveBeenCalled();
  });

  it('submits a public account request', async () => {
    render(<MemoryRouter><RegistrationRequestPage /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('Full name'), 'Morgan Employee');
    await userEvent.type(screen.getByLabelText('Work email'), 'morgan@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Submit request' }));
    expect(await screen.findByRole('status')).toHaveTextContent('administrator will review');
    expect(apiPost).toHaveBeenCalledWith('/auth/registration-requests', {
      fullName: 'Morgan Employee',
      email: 'morgan@example.com',
    });
  });

  it('requests a password reset for the supplied email', async () => {
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('Registered email'), 'morgan@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Email reset link' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If an active account uses that email');
    expect(apiPost).toHaveBeenCalledWith('/auth/password-reset-requests', { email: 'morgan@example.com' });
  });

  it('submits the token from the email fragment to set a password', async () => {
    render(<MemoryRouter initialEntries={['/set-password#token=single-use-token']}><PasswordActionPage /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('New password'), 'Secure-pass12');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Secure-pass12');
    await userEvent.click(screen.getByRole('button', { name: 'Set password' }));
    await waitFor(() => expect(apiPost).toHaveBeenCalledWith('/auth/password/complete', {
      token: 'single-use-token',
      newPassword: 'Secure-pass12',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Your password is set');
  });
});
