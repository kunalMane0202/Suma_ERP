import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Avatar, PageHeader } from '@/components/common';
import { IconTile } from '@/components/layout';
import { KeyRound, SlidersHorizontal } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { api, errorMessage } from '@/services/api';
import { titleCase } from '@/utils/format';

const schema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password'),
  newPassword: z.string().min(10, 'At least 10 characters').regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, 'Needs upper and lower case letters and a digit'),
  confirm: z.string(),
}).refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });
type Form = z.infer<typeof schema>;

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });
  if (!user) return null;
  const submit = async (v: Form) => {
    try { await api.post('/auth/change-password', { currentPassword: v.currentPassword, newPassword: v.newPassword }); toast.success('Password changed'); reset(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const setDark = async (dark: boolean) => {
    setTheme(dark ? 'DARK' : 'LIGHT');
    try { const r = await api.put('/auth/me/preferences', { themePreference: dark ? 'DARK' : 'LIGHT' }); updateUser(r.data); } catch { /* keep local */ }
  };
  return (
    <>
      <PageHeader title="Profile" />
      <Card tone="brand" className="relative mb-5 overflow-hidden">
        <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
        <div className="relative flex flex-col gap-5 p-6 sm:flex-row sm:items-center">
          <Avatar name={user.fullName} className="h-20 w-20 text-2xl ring-4 ring-white/30" />
          <div className="min-w-0 flex-1">
            <p className="font-display text-2xl font-semibold">{user.fullName}</p>
            <p className="text-sm text-white/75">{user.employeeCode} · {user.email}</p>
            <div className="mt-3 flex flex-wrap gap-1.5">
              <span className="rounded-full bg-white/20 px-3 py-1 text-xs font-semibold">{titleCase(user.role)}</span>
              {user.projects.map((p) => <span key={p.id} className="rounded-full bg-white/10 px-3 py-1 text-xs">{p.name}{p.designation !== 'MEMBER' ? ` · ${titleCase(p.designation)}` : ''}</span>)}
            </div>
          </div>
        </div>
      </Card>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><div className="flex items-center gap-3"><IconTile icon={SlidersHorizontal} tone="soft" /><div><CardTitle>Preferences</CardTitle><CardDescription>How SUMA looks for you</CardDescription></div></div></CardHeader>
          <CardContent className="space-y-4">
            <div><p className="text-sm font-medium">Projects</p>
              <div className="mt-1 flex flex-wrap gap-1">{user.projects.length ? user.projects.map((p) => <Badge key={p.id} tone={p.designation === 'PROJECT_LEAD' ? 'navy' : 'neutral'}>{p.name}{p.designation !== 'MEMBER' ? ` · ${titleCase(p.designation)}` : ''}</Badge>) : <span className="text-sm text-muted-foreground">None</span>}</div></div>
            <label className="flex items-center justify-between rounded-xl bg-muted/60 p-4 text-sm"><span><span className="font-medium">Dark mode</span><span className="block text-xs text-muted-foreground">Light mode is the default. Your choice is saved to your account.</span></span>
              <Switch checked={theme === 'DARK'} onCheckedChange={setDark} aria-label="Dark mode" /></label>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><div className="flex items-center gap-3"><IconTile icon={KeyRound} tone="brand" /><div><CardTitle>Change password</CardTitle><CardDescription>At least 10 characters with upper and lower case letters and a digit</CardDescription></div></div></CardHeader>
          <CardContent>
            <form className="space-y-3" onSubmit={handleSubmit(submit)} noValidate>
              <Field label="Current password" htmlFor="cp" error={errors.currentPassword?.message}><Input id="cp" type="password" autoComplete="current-password" {...register('currentPassword')} /></Field>
              <Field label="New password" htmlFor="np" error={errors.newPassword?.message}><Input id="np" type="password" autoComplete="new-password" {...register('newPassword')} /></Field>
              <Field label="Confirm new password" htmlFor="cf" error={errors.confirm?.message}><Input id="cf" type="password" autoComplete="new-password" {...register('confirm')} /></Field>
              <Button type="submit" loading={isSubmitting}>Change password</Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
