import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export const Table = ({ className, ...props }: HTMLAttributes<HTMLTableElement>) => (
  <div className="w-full overflow-x-auto px-2"><table className={cn('w-full caption-bottom border-separate border-spacing-y-1 text-sm', className)} {...props} /></div>
);
export const THead = ({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) => <thead className={className} {...props} />;
export const TBody = ({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) => <tbody className={className} {...props} />;
export const TR = ({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) => (
  <tr className={cn('group transition-colors [&>td:first-child]:rounded-l-xl [&>td:last-child]:rounded-r-xl [&>td]:transition-colors hover:[&>td]:bg-accent/70', className)} {...props} />
);
export const TH = ({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) => (
  <th className={cn('h-9 whitespace-nowrap px-3 text-left align-middle text-[11px] font-semibold uppercase tracking-wide text-muted-foreground/80', className)} {...props} />
);
export const TD = ({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) => <td className={cn('px-3 py-3 align-middle', className)} {...props} />;
