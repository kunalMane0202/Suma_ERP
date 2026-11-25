import * as TP from '@radix-ui/react-tooltip';
import type { ReactNode } from 'react';

export const TooltipProvider = TP.Provider;

export function Tooltip({ content, children, side = 'top' }: { content: ReactNode; children: ReactNode; side?: 'top' | 'right' | 'bottom' | 'left' }) {
  return (
    <TP.Root delayDuration={250}>
      <TP.Trigger asChild>{children}</TP.Trigger>
      <TP.Portal>
        <TP.Content side={side} sideOffset={6} className="z-50 max-w-xs rounded-md bg-navy px-2.5 py-1.5 text-xs text-white shadow-lift data-[state=delayed-open]:animate-in data-[state=delayed-open]:fade-in-0">
          {content}
        </TP.Content>
      </TP.Portal>
    </TP.Root>
  );
}
