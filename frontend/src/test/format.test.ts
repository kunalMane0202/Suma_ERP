import { clock, duration, titleCase } from '@/utils/format';

describe('format helpers', () => {
  it('formats durations', () => {
    expect(duration(0)).toBe('0s');
    expect(duration(65)).toBe('1m 05s');
    expect(duration(3725)).toBe('1h 02m');
    expect(duration(null)).toBe('—');
  });
  it('formats running clocks', () => {
    expect(clock(3725)).toBe('01:02:05');
    expect(clock(-5)).toBe('00:00:00');
  });
  it('turns codes into sentence case', () => {
    expect(titleCase('MISSING_LOGOUT')).toBe('Missing logout');
    expect(titleCase(undefined)).toBe('');
  });
});
