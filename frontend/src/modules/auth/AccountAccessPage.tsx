import { zodResolver } from '@hookform/resolvers/zod';
import { useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { api, errorMessage } from '@/services/api';

const requestSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(150),
  email: z.string().trim().email('Enter a valid work email').max(200),
});
type RequestForm = z.infer<typeof requestSchema>;

const emailSchema = z.object({ email: z.string().trim().email('Enter a valid email').max(200) });
type EmailForm = z.infer<typeof emailSchema>;

const passwordSchema = z.object({
  password: z.string().min(10, 'At least 10 characters').max(100)
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, 'Needs upper and lower case letters and a digit'),
  confirmPassword: z.string(),
}).refine((value) => value.password === value.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords do not match',
});
type PasswordForm = z.infer<typeof passwordSchema>;

function AccountPageFrame({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <main className="grid min-h-screen place-items-center px-4 py-10">
      <section className="glass w-full max-w-md p-8">
        <div className="mb-6 flex items-center gap-2.5">
          <img src="/favicon.svg" alt="" width={36} height={36} className="h-9 w-9 rounded-xl" />
          <span className="font-display font-bold">SUMA Tracking Tool</span>
        </div>
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{description}</p>
        {children}
        <p className="mt-6 text-center text-sm text-muted-foreground"><Link to="/login" className="font-medium text-primary hover:underline">Back to sign in</Link></p>
      </section>
    </main>
  );
}

export function RegistrationRequestPage() {
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RequestForm>({ resolver: zodResolver(requestSchema) });
  const submit = async (value: RequestForm) => {
    setError(null);
    try {
      await api.post('/auth/registration-requests', value);
      setSubmitted(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AccountPageFrame title="Request an account" description="Submit your name and work email. An administrator will review your request.">
      {submitted ? (
        <p role="status" className="mt-6 rounded-xl bg-ok/10 p-4 text-sm text-foreground">
          Your request has been submitted. If eligible, an administrator will review it and email you setup instructions.
        </p>
      ) : (
        <form className="mt-6 space-y-4" onSubmit={handleSubmit(submit)} noValidate>
          <Field label="Full name" htmlFor="request-name" error={errors.fullName?.message}>
            <Input id="request-name" autoComplete="name" {...register('fullName')} />
          </Field>
          <Field label="Work email" htmlFor="request-email" error={errors.email?.message}>
            <Input id="request-email" type="email" autoComplete="email" {...register('email')} />
          </Field>
          {error && <p role="alert" className="rounded-xl bg-breach/10 px-3.5 py-2.5 text-sm text-breach">{error}</p>}
          <Button type="submit" className="w-full" loading={isSubmitting}>Submit request</Button>
        </form>
      )}
    </AccountPageFrame>
  );
}

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<EmailForm>({ resolver: zodResolver(emailSchema) });
  const submit = async (value: EmailForm) => {
    setError(null);
    try {
      await api.post('/auth/password-reset-requests', value);
      setSubmitted(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AccountPageFrame title="Reset your password" description="Enter your registered email to receive a single-use password reset link.">
      {submitted ? (
        <p role="status" className="mt-6 rounded-xl bg-ok/10 p-4 text-sm text-foreground">
          If an active account uses that email, a reset link will be sent. If you cannot access your email, contact an administrator for a manual reset.
        </p>
      ) : (
        <form className="mt-6 space-y-4" onSubmit={handleSubmit(submit)} noValidate>
          <Field label="Registered email" htmlFor="reset-email" error={errors.email?.message}>
            <Input id="reset-email" type="email" autoComplete="email" {...register('email')} />
          </Field>
          {error && <p role="alert" className="rounded-xl bg-breach/10 px-3.5 py-2.5 text-sm text-breach">{error}</p>}
          <Button type="submit" className="w-full" loading={isSubmitting}>Email reset link</Button>
        </form>
      )}
    </AccountPageFrame>
  );
}

export function PasswordActionPage() {
  const location = useLocation();
  const token = new URLSearchParams(location.hash.slice(1)).get('token') ?? '';
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<PasswordForm>({ resolver: zodResolver(passwordSchema) });
  const submit = async (value: PasswordForm) => {
    setError(null);
    try {
      await api.post('/auth/password/complete', { token, newPassword: value.password });
      setCompleted(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AccountPageFrame title="Choose a password" description="Use a strong password with at least 10 characters, upper and lower case letters, and a digit.">
      {!token ? (
        <p role="alert" className="mt-6 rounded-xl bg-breach/10 p-4 text-sm text-breach">This password link is missing or invalid. Request a new one from the sign-in page.</p>
      ) : completed ? (
        <div className="mt-6 space-y-4">
          <p role="status" className="rounded-xl bg-ok/10 p-4 text-sm text-foreground">Your password is set. You can now sign in.</p>
          <Button asChild className="w-full"><Link to="/login">Continue to sign in</Link></Button>
        </div>
      ) : (
        <form className="mt-6 space-y-4" onSubmit={handleSubmit(submit)} noValidate>
          <Field label="New password" htmlFor="new-password" error={errors.password?.message}>
            <Input id="new-password" type="password" autoComplete="new-password" {...register('password')} />
          </Field>
          <Field label="Confirm password" htmlFor="confirm-password" error={errors.confirmPassword?.message}>
            <Input id="confirm-password" type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </Field>
          {error && <p role="alert" className="rounded-xl bg-breach/10 px-3.5 py-2.5 text-sm text-breach">{error}</p>}
          <Button type="submit" className="w-full" loading={isSubmitting}>Set password</Button>
        </form>
      )}
    </AccountPageFrame>
  );
}
