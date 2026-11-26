import { cva, type VariantProps } from 'class-variance-authority';
import { forwardRef, type HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

const tile = cva('min-w-0 rounded-2xl text-card-foreground', {
  variants: {
    tone: {
      plain: 'glass',
      brand: 'bg-brand text-white shadow-glow',
      tint: 'bg-brand-soft shadow-[inset_0_0_0_1px_hsl(var(--primary)/.15)]',
      ink: 'bg-navy text-white shadow-lift dark:bg-[#26224A]',
    },
  },
  defaultVariants: { tone: 'plain' },
});

export type CardProps = HTMLAttributes<HTMLDivElement> & VariantProps<typeof tile>;

export const Card = forwardRef<HTMLDivElement, CardProps>(({ className, tone, ...props }, ref) => (
  <div ref={ref} className={cn(tile({ tone }), className)} {...props} />
));
Card.displayName = 'Card';

export const CardHeader = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('flex items-start justify-between gap-3 px-6 pt-5 pb-3', className)} {...props} />
);
export const CardTitle = ({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) => (
  <h3 className={cn('font-display text-[16px] font-semibold leading-tight', className)} {...props} />
);
export const CardDescription = ({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) => (
  <p className={cn('mt-1 text-[13px] text-muted-foreground', className)} {...props} />
);
export const CardContent = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('px-6 pb-6', className)} {...props} />
);
