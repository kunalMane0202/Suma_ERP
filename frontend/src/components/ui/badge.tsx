import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

const badgeVariants = cva('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap before:h-1.5 before:w-1.5 before:rounded-full before:bg-current before:opacity-80', {
  variants: {
    tone: {
      neutral: 'bg-secondary text-secondary-foreground',
      blue: 'bg-primary/10 text-primary',
      green: 'bg-ok/15 text-ok',
      amber: 'bg-warn/15 text-warn',
      red: 'bg-breach/15 text-breach',
      violet: 'bg-idle/15 text-idle',
      navy: 'bg-brand text-white before:bg-white',
    },
  },
  defaultVariants: { tone: 'neutral' },
});

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>['tone']>;

export function Badge({ className, tone, ...props }: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}
