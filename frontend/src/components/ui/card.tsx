import { forwardRef, type HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('glass min-w-0 rounded-lg text-card-foreground', className)} {...props} />
));
Card.displayName = 'Card';

export const CardHeader = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('flex items-start justify-between gap-3 px-5 pt-4 pb-2', className)} {...props} />
);
export const CardTitle = ({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) => (
  <h3 className={cn('text-[15px] font-semibold leading-tight', className)} {...props} />
);
export const CardDescription = ({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) => (
  <p className={cn('text-xs text-muted-foreground mt-0.5', className)} {...props} />
);
export const CardContent = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('px-5 pb-5', className)} {...props} />
);
