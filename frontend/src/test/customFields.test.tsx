import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { CustomFieldInputs, formatCustomValue, missingRequired, type CustomFieldDef } from '@/modules/tickets/customFields';

const defs: CustomFieldDef[] = [
  { id: '1', fieldKey: 'asset_tag', label: 'Asset tag', fieldType: 'TEXT', required: true },
  { id: '2', fieldKey: 'site', label: 'Site', fieldType: 'SELECT', options: 'Pune, Mumbai', required: false },
  { id: '3', fieldKey: 'warranty', label: 'Under warranty', fieldType: 'BOOLEAN', required: false },
];

function Harness({ showErrors = false }: { showErrors?: boolean }) {
  const [v, setV] = useState<Record<string, string>>({});
  return <><CustomFieldInputs defs={defs} values={v} onChange={setV} showErrors={showErrors} /><output data-testid="out">{JSON.stringify(v)}</output></>;
}

describe('custom ticket fields', () => {
  it('lists required fields that are still empty', () => {
    expect(missingRequired(defs, {})).toEqual(['Asset tag']);
    expect(missingRequired(defs, { asset_tag: '  ' })).toEqual(['Asset tag']);
    expect(missingRequired(defs, { asset_tag: 'LT-1' })).toEqual([]);
  });

  it('formats stored values for display', () => {
    expect(formatCustomValue(defs[2], true)).toBe('Yes');
    expect(formatCustomValue(defs[2], 'false')).toBe('No');
    expect(formatCustomValue(defs[0], '')).toBe('—');
    expect(formatCustomValue(undefined, 42)).toBe('42');
  });

  it('renders a labelled control per field type and reports values', async () => {
    render(<Harness />);
    await userEvent.type(screen.getByLabelText('Asset tag *'), 'LT-9');
    await userEvent.selectOptions(screen.getByLabelText('Site'), 'Mumbai');
    await userEvent.selectOptions(screen.getByLabelText('Under warranty'), 'true');
    expect(JSON.parse(screen.getByTestId('out').textContent!)).toEqual({ asset_tag: 'LT-9', site: 'Mumbai', warranty: 'true' });
  });

  it('shows an inline error for an empty required field once the form was submitted', () => {
    render(<Harness showErrors />);
    expect(screen.getByText('Asset tag is required')).toBeInTheDocument();
  });
});
