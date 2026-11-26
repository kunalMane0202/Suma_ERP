import { zodResolver } from '@hookform/resolvers/zod';
import { motion } from 'motion/react';
import { Lock, ShieldCheck } from 'lucide-react';
import { LiveDot, RadialGauge } from '@/components/viz';
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
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      <section className="relative hidden overflow-hidden bg-[#17142E] p-12 text-white lg:flex lg:flex-col">
        {/* gradient mesh */}
        <div className="pointer-events-none absolute -left-24 -top-24 h-[420px] w-[420px] rounded-full bg-[#5B4CF0] opacity-60 blur-[110px]" />
        <div className="pointer-events-none absolute right-[-80px] top-1/3 h-[360px] w-[360px] rounded-full bg-[#8B5CF6] opacity-50 blur-[110px]" />
        <div className="pointer-events-none absolute bottom-[-120px] left-1/3 h-[360px] w-[360px] rounded-full bg-[#F0506E] opacity-30 blur-[120px]" />
        <div className="pointer-events-none absolute inset-0 opacity-[.08] [background-image:radial-gradient(#fff_1px,transparent_1px)] [background-size:18px_18px]" />

        <div className="relative flex items-center gap-3"><img src="/favicon.svg" alt="" className="h-10 w-10 rounded-xl shadow-glow" /><span className="font-display text-lg font-bold">SUMA Tracking Tool</span></div>

        {/* floating bento preview */}
        <div className="relative mx-auto mt-14 grid w-full max-w-[520px] grid-cols-5 gap-4" aria-hidden>
          <motion.div initial={{ opacity: 0, y: 30, rotate: -4 }} animate={{ opacity: 1, y: 0, rotate: -3 }} transition={{ type: 'spring', stiffness: 120, damping: 16, delay: 0.1 }}
            className="col-span-3 rounded-[22px] bg-gradient-to-br from-[#5B4CF0] to-[#8B5CF6] p-5 shadow-[0_30px_60px_-20px_rgba(91,76,240,.8)]">
            <p className="text-xs text-white/75">Tickets this month</p>
            <p className="display-num mt-2 text-5xl">1,284</p>
            <svg viewBox="0 0 200 50" className="mt-3 h-12 w-full"><path d="M0 40 L25 32 L50 36 L75 20 L100 26 L125 12 L150 18 L175 6 L200 10" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 30, rotate: 5 }} animate={{ opacity: 1, y: 0, rotate: 4 }} transition={{ type: 'spring', stiffness: 120, damping: 16, delay: 0.25 }}
            className="col-span-2 grid place-items-center rounded-[22px] bg-white p-4 text-[#17142E] shadow-2xl">
            <RadialGauge value={97} size={110} stroke={11} tone="ok" label={<span className="display-num text-2xl">97%</span>} sublabel="SLA met" />
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 30, rotate: 3 }} animate={{ opacity: 1, y: 0, rotate: 2 }} transition={{ type: 'spring', stiffness: 120, damping: 16, delay: 0.4 }}
            className="col-span-5 rounded-[22px] bg-white/10 p-5 ring-1 ring-white/15 backdrop-blur-xl">
            <div className="mb-3 flex items-center justify-between text-xs text-white/75"><span className="flex items-center gap-2"><LiveDot /> Today's shift</span><span className="num">09:00 – 18:00</span></div>
            <div className="flex h-4 gap-1">
              {[['bg-gradient-to-r from-[#5B4CF0] to-[#8B5CF6]', '34%'], ['bg-[#F5A524]', '6%'], ['bg-gradient-to-r from-[#5B4CF0] to-[#8B5CF6]', '30%'], ['bg-[#A78BFA]', '4%'], ['bg-gradient-to-r from-[#5B4CF0] to-[#8B5CF6]', '20%']].map(([c, w], i) => (
                <motion.div key={i} className={`rounded-full ${c}`} initial={{ width: 0 }} animate={{ width: w }} transition={{ duration: 0.7, delay: 0.7 + i * 0.15, ease: [0.16, 1, 0.3, 1] }} />
              ))}
            </div>
          </motion.div>
        </div>

        <div className="relative mt-auto max-w-md">
          <h1 className="text-[38px] font-bold leading-[1.1]">Every ticket, SLA clock and shift in one place.</h1>
          <p className="mt-4 text-white/70">Service desk operations, SLA tracking and workforce attendance for support teams across clients and projects.</p>
        </div>
      </section>
      <section className="relative flex items-center justify-center px-4 py-12">
        <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} className="glass w-full max-w-[400px] p-8">
          <div className="mb-6 flex items-center gap-2.5 lg:hidden"><img src="/favicon.svg" alt="" className="h-9 w-9 rounded-xl" /><span className="font-display font-bold">SUMA Tracking Tool</span></div>
          <h2 className="text-[26px] font-bold">Welcome back</h2>
          <p className="mt-1.5 text-sm text-muted-foreground">Sign in with your SUMA account. Your sign-in starts today's attendance record.</p>
          <form className="mt-7 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Field label="Username or email" htmlFor="username" error={errors.username?.message}>
              <Input id="username" autoComplete="username" autoFocus className="h-11" aria-invalid={!!errors.username} {...register('username')} />
            </Field>
            <Field label="Password" htmlFor="password" error={errors.password?.message}>
              <Input id="password" type="password" autoComplete="current-password" className="h-11" aria-invalid={!!errors.password} {...register('password')} />
            </Field>
            {error && <p role="alert" className="rounded-xl bg-breach/10 px-3.5 py-2.5 text-sm font-medium text-breach">{error}</p>}
            <Button type="submit" className="w-full" size="lg" loading={isSubmitting}><Lock /> Sign in</Button>
          </form>
          <div className="mt-7 flex gap-3 rounded-xl bg-muted/70 p-3.5 text-xs leading-relaxed text-muted-foreground">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
            <p>Sign-ins are recorded with your network address for security. While signed in, SUMA records your sign-in and sign-out times,
              breaks and periods without activity in this app. You can review everything recorded under My attendance.</p>
          </div>
        </motion.div>
      </section>
    </div>
  );
}
