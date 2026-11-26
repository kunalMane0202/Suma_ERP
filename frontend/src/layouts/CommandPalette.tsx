import * as DialogPrimitive from '@radix-ui/react-dialog';
import { CornerDownLeft, Search, Ticket } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { visibleNav } from '@/routes/nav';
import { api } from '@/services/api';
import { cn } from '@/utils/cn';
import type { Page, TicketSummary } from '@/types/api';

interface Item { key: string; label: string; hint: string; icon: React.ReactNode; go: () => void }

/** Ctrl/⌘+K: jump to any page you can access, or search tickets by number or title. */
export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { user, can } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [active, setActive] = useState(0);

  useEffect(() => { if (!open) { setQ(''); setTickets([]); setActive(0); } }, [open]);
  useEffect(() => {
    if (!open || q.trim().length < 2 || !can('TICKET_READ')) { setTickets([]); return; }
    const h = setTimeout(() => {
      api.get<Page<TicketSummary>>('/tickets', { params: { q, size: 5 } }).then((r) => setTickets(r.data.content)).catch(() => setTickets([]));
    }, 200);
    return () => clearTimeout(h);
  }, [q, open, can]);

  const items = useMemo<Item[]>(() => {
    if (!user) return [];
    const term = q.toLowerCase().trim();
    const pages = visibleNav(user).flatMap((g) => g.items.map((i) => ({ ...i, group: g.label })))
      .filter((i) => !term || i.label.toLowerCase().includes(term))
      .map<Item>((i) => ({ key: i.to, label: i.label, hint: i.group, icon: <i.icon className="h-4 w-4" />, go: () => navigate(i.to) }));
    const t = tickets.map<Item>((x) => ({ key: x.id, label: x.title, hint: x.ticketNumber, icon: <Ticket className="h-4 w-4" />, go: () => navigate(`/tickets/${x.id}`) }));
    return [...pages, ...t];
  }, [user, q, tickets, navigate]);

  const run = (i: Item) => { i.go(); onOpenChange(false); };
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-navy-900/30 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0" />
        <DialogPrimitive.Content className="fixed left-1/2 top-[14vh] z-50 w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 overflow-hidden rounded-2xl bg-card shadow-lift ring-1 ring-border/60 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95">
          <DialogPrimitive.Title className="sr-only">Search SUMA</DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">Jump to a page or find a ticket</DialogPrimitive.Description>
          <div className="flex items-center gap-3 border-b px-5">
            <Search className="h-4 w-4 text-muted-foreground" />
            <input autoFocus aria-label="Search pages and tickets" placeholder="Search pages and tickets…" value={q}
              onChange={(e) => { setQ(e.target.value); setActive(0); }}
              onKeyDown={(e) => {
                if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(items.length - 1, a + 1)); }
                if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(0, a - 1)); }
                if (e.key === 'Enter' && items[active]) run(items[active]);
              }}
              className="h-14 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted-foreground focus-visible:ring-0 focus-visible:ring-offset-0" />
            <kbd className="rounded-md bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">Esc</kbd>
          </div>
          <ul className="max-h-80 overflow-y-auto p-2" role="listbox">
            {items.length === 0 && <li className="px-3 py-8 text-center text-sm text-muted-foreground">Nothing matches “{q}”.</li>}
            {items.map((i, idx) => (
              <li key={i.key} role="option" aria-selected={idx === active}>
                <button onMouseEnter={() => setActive(idx)} onClick={() => run(i)}
                  className={cn('flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm', idx === active ? 'bg-brand text-white' : 'hover:bg-accent')}>
                  <span className={cn('grid h-8 w-8 place-items-center rounded-lg', idx === active ? 'bg-white/20' : 'bg-secondary')}>{i.icon}</span>
                  <span className="flex-1 truncate font-medium">{i.label}</span>
                  <span className={cn('text-xs', idx === active ? 'text-white/80' : 'text-muted-foreground')}>{i.hint}</span>
                  {idx === active && <CornerDownLeft className="h-3.5 w-3.5" />}
                </button>
              </li>
            ))}
          </ul>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
