import { useEffect, useState } from 'react';
import { Input, NativeSelect } from '@/components/ui/input';
import { Field } from '@/components/ui/label';
import { api } from '@/services/api';
import { date } from '@/utils/format';

export interface CustomFieldDef { id: string; fieldKey: string; label: string; fieldType: 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'BOOLEAN'; options?: string; required: boolean }

/** Active custom-field definitions that apply to a project's tickets. */
export function useCustomFields(projectId?: string) {
  const [defs, setDefs] = useState<CustomFieldDef[]>([]);
  useEffect(() => {
    if (!projectId) { setDefs([]); return; }
    let live = true;
    api.get<CustomFieldDef[]>('/custom-fields', { params: { projectId } }).then((r) => live && setDefs(r.data)).catch(() => live && setDefs([]));
    return () => { live = false; };
  }, [projectId]);
  return defs;
}

/** Names of required fields that are still empty. */
export function missingRequired(defs: CustomFieldDef[], values: Record<string, string>) {
  return defs.filter((d) => d.required && !(values[d.fieldKey] ?? '').toString().trim()).map((d) => d.label);
}

export function CustomFieldInputs({ defs, values, onChange, showErrors }: { defs: CustomFieldDef[]; values: Record<string, string>; onChange: (v: Record<string, string>) => void; showErrors?: boolean }) {
  if (!defs.length) return null;
  const set = (k: string, v: string) => onChange({ ...values, [k]: v });
  return (
    <>
      {defs.map((d) => {
        const id = `cf-${d.fieldKey}`;
        const v = values[d.fieldKey] ?? '';
        const err = showErrors && d.required && !v.trim() ? `${d.label} is required` : undefined;
        const label = d.required ? `${d.label} *` : d.label;
        return (
          <Field key={d.id} label={label} htmlFor={id} error={err}>
            {d.fieldType === 'SELECT' ? (
              <NativeSelect id={id} value={v} onChange={(e) => set(d.fieldKey, e.target.value)}><option value="">Choose</option>{(d.options ?? '').split(',').map((o) => o.trim()).filter(Boolean).map((o) => <option key={o} value={o}>{o}</option>)}</NativeSelect>
            ) : d.fieldType === 'BOOLEAN' ? (
              <NativeSelect id={id} value={v} onChange={(e) => set(d.fieldKey, e.target.value)}><option value="">Not set</option><option value="true">Yes</option><option value="false">No</option></NativeSelect>
            ) : (
              <Input id={id} type={d.fieldType === 'NUMBER' ? 'number' : d.fieldType === 'DATE' ? 'date' : 'text'} value={v} onChange={(e) => set(d.fieldKey, e.target.value)} aria-invalid={!!err} />
            )}
          </Field>
        );
      })}
    </>
  );
}

/** Formats a stored value for display. */
export function formatCustomValue(def: CustomFieldDef | undefined, v: unknown): string {
  if (v === null || v === undefined || v === '') return '—';
  if (def?.fieldType === 'BOOLEAN') return v === true || v === 'true' ? 'Yes' : 'No';
  if (def?.fieldType === 'DATE') return date(String(v));
  return String(v);
}
