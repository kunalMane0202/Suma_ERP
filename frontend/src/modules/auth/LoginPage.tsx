import { zodResolver } from '@hookform/resolvers/zod';
import { motion } from 'motion/react';
import { Lock } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { errorMessage } from '@/services/api';

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username or email'),
  password: z.string().min(1, 'Enter your password'),
});
type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { login, user, ready } = useAuth();
  const { setTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (ready && user) return <Navigate to="/" replace />;

  const onSubmit = async (v: FormValues) => {
    setError(null);
    try {
      const me = await login(v.username, v.password);
      setTheme(me.themePreference === 'DARK' ? 'DARK' : 'LIGHT');
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/', { replace: true });
    } catch (e) {
      setError(errorMessage(e, 'Sign-in failed. Try again.'));
    }
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <section className="relative hidden overflow-hidden bg-navy p-12 text-white lg:flex lg:flex-col">
        <div className="flex items-center gap-2.5"><img src="/favicon.svg" alt="" className="h-9 w-9" /><span className="text-lg font-semibold">SUMA Tracking Tool</span></div>
        <div className="mt-auto max-w-md">
          <h1 className="text-[40px] font-semibold leading-[1.1] tracking-tight">Every ticket, every SLA clock and every shift in one place.</h1>
          <p className="mt-4 text-white/70">Service desk operations, SLA tracking and workforce attendance for support teams across clients and projects.</p>
        </div>
        {/* A stylised shift strip: the product's signature view */}
        <div className="mt-10 max-w-md" aria-hidden>
          <div className="flex h-3 overflow-hidden rounded-full bg-white/10">
            <motion.div className="bg-[#7FA8FF]" initial={{ width: 0 }} animate={{ width: '34%' }} transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }} />
            <motion.div className="bg-[#E9B949]" initial={{ width: 0 }} animate={{ width: '6%' }} transition={{ duration: 0.4, delay: 0.8 }} />
            <motion.div className="bg-[#7FA8FF]" initial={{ width: 0 }} animate={{ width: '30%' }} transition={{ duration: 0.8, delay: 1.1, ease: [0.16, 1, 0.3, 1] }} />
            <motion.div className="bg-[#E9B949]" initial={{ width: 0 }} animate={{ width: '4%' }} transition={{ duration: 0.3, delay: 1.8 }} />
            <motion.div className="bg-[#7FA8FF]" initial={{ width: 0 }} animate={{ width: '18%' }} transition={{ duration: 0.6, delay: 2, ease: [0.16, 1, 0.3, 1] }} />
          </div>
          <div className="mt-2 flex justify-between text-xs text-white/50 num"><span>09:00</span><span>13:00</span><span>18:00</span></div>
        </div>
      </section>
      <section className="flex items-center justify-center px-4 py-12">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35 }} className="glass w-full max-w-sm rounded-xl p-7">
          <div className="mb-6 lg:hidden flex items-center gap-2"><img src="/favicon.svg" alt="" className="h-8 w-8" /><span className="font-semibold">SUMA Tracking Tool</span></div>
          <h2 className="text-xl font-semibold">Sign in</h2>
          <p className="mt-1 text-sm text-muted-foreground">Use your SUMA account. Your sign-in starts today's attendance record.</p>
          <form className="mt-6 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Field label="Username or email" htmlFor="username" error={errors.username?.message}>
              <Input id="username" autoComplete="username" autoFocus aria-invalid={!!errors.username} {...register('username')} />
            </Field>
            <Field label="Password" htmlFor="password" error={errors.password?.message}>
              <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!errors.password} {...register('password')} />
            </Field>
            {error && <p role="alert" className="rounded-md border border-breach/30 bg-breach/5 px-3 py-2 text-sm text-breach">{error}</p>}
            <Button type="submit" className="w-full" size="lg" loading={isSubmitting}><Lock /> Sign in</Button>
          </form>
          <p className="mt-6 text-xs leading-relaxed text-muted-foreground">
            Sign-ins are recorded with your network address for security. While signed in, SUMA records your sign-in and sign-out times,
            breaks and periods without activity in this app. You can review everything recorded under My attendance.
          </p>
        </motion.div>
      </section>
    </div>
  );
}
