import { render, screen } from '@testing-library/react';
import { ShiftTimeline } from '@/components/ShiftTimeline';
import { TooltipProvider } from '@/components/ui/tooltip';

describe('ShiftTimeline', () => {
  it('describes sessions, breaks and inactivity for screen readers', () => {
    render(
      <TooltipProvider>
        <ShiftTimeline data={{
          date: '2026-03-02', timezone: 'UTC',
          sessions: [{ loginAt: '2026-03-02T09:00:00Z', logoutAt: '2026-03-02T18:00:00Z' }],
          breaks: [{ startedAt: '2026-03-02T12:00:00Z', endedAt: '2026-03-02T12:30:00Z', durationSeconds: 1800 }],
          idles: [{ startedAt: '2026-03-02T15:00:00Z', endedAt: '2026-03-02T15:25:00Z' }],
        }} />
      </TooltipProvider>,
    );
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', 'Timeline for 2026-03-02: 1 session(s), 1 break(s), 1 inactivity event(s)');
    expect(screen.getByText('08:00')).toBeInTheDocument();
  });
});
