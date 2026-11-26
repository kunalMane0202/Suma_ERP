import * as TabsPrimitive from '@radix-ui/react-tabs';
import { forwardRef, type ComponentPropsWithoutRef, type ReactNode } from 'react';
import { useUrlState } from '@/hooks/useUrlState';
import { cn } from '@/utils/cn';

export const Tabs = TabsPrimitive.Root;
export const TabsList = forwardRef<HTMLDivElement, ComponentPropsWithoutRef<typeof TabsPrimitive.List>>(({ className, ...props }, ref) => (
  <TabsPrimitive.List ref={ref} className={cn('inline-flex h-11 max-w-full items-center gap-1 overflow-x-auto rounded-full bg-card p-1 text-muted-foreground shadow-glass', className)} {...props} />
));
TabsList.displayName = 'TabsList';
export const TabsTrigger = forwardRef<HTMLButtonElement, ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>>(({ className, ...props }, ref) => (
  <TabsPrimitive.Trigger ref={ref} className={cn('inline-flex h-9 items-center justify-center whitespace-nowrap rounded-full px-4 text-[13px] font-semibold transition-[color,background-color,box-shadow] hover:text-foreground data-[state=active]:bg-brand data-[state=active]:text-white data-[state=active]:shadow-glow', className)} {...props} />
));
TabsTrigger.displayName = 'TabsTrigger';
export const TabsContent = forwardRef<HTMLDivElement, ComponentPropsWithoutRef<typeof TabsPrimitive.Content>>(({ className, ...props }, ref) => (
  <TabsPrimitive.Content ref={ref} className={cn('mt-5 focus-visible:outline-none data-[state=active]:animate-in data-[state=active]:fade-in-0 data-[state=active]:slide-in-from-bottom-1', className)} {...props} />
));
TabsContent.displayName = 'TabsContent';

/** Tabs whose selected tab is kept in the address (?tab=), so a tab can be reloaded, bookmarked and shared. */
export function UrlTabs({ fallback, children, param = 'tab', className }: { fallback: string; children: ReactNode; param?: string; className?: string }) {
  const [value, setValue] = useUrlState(param, fallback);
  return <TabsPrimitive.Root value={value} onValueChange={setValue} className={className}>{children}</TabsPrimitive.Root>;
}
