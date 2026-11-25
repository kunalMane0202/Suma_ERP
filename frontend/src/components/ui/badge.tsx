import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

const badgeVariants = cva('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap', {
  variants: {
    tone: {
      neutral: 'border-border bg-secondary text-secondary-foreground',
      blue: 'border-primary/20 bg-primary/10 text-primary',
      green: 'border-ok/25 bg-ok/10 text-ok',
      amber: 'border-warn/30 bg-warn/10 text-warn',
      red: 'border-breach/25 bg-breach/10 text-breach',
      violet: 'border-idle/25 bg-idle/10 text-idle',
      navy: 'border-transparent bg-navy text-white',
    },
  },
  defaultVariants: { tone: 'neutral' },
});

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>['tone']>;

export function Badge({ className, tone, ...props }: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}
