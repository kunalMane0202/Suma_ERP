import { expect, test } from '@playwright/test';
import { ADMIN, admin, uiLogin } from './helpers';

// Uses the Globex project so a temporary required field cannot affect other specs that create Acme tickets.
const PROJECT = 'Globex Core Banking L2';

test('custom ticket field: defined by an admin, required on the ticket form, shown on the ticket', async ({ page }) => {
  const key = 'e2e_' + Date.now().toString(36);
  const label = 'E2E asset ' + key.slice(4);
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/settings?tab=fields');
  await page.getByRole('button', { name: 'Add field' }).click();
  const d = page.getByRole('dialog', { name: 'Add custom field' });
  await d.getByLabel('Label').fill(label);
  await d.getByLabel('Key').fill(key);
  await d.getByLabel('Applies to').selectOption({ label: PROJECT });
  await d.getByRole('switch').first().click(); // Required
  await d.getByRole('button', { name: 'Save field' }).click();
  await expect(page.getByText(label)).toBeVisible();

  try {
    await page.goto('/tickets');
    await page.getByRole('button', { name: 'New ticket' }).click();
    const t = page.getByRole('dialog', { name: 'New ticket' });
    await t.getByLabel('Project').selectOption({ label: PROJECT });
    await t.getByLabel('Title').fill('Custom field ' + key);
    await t.getByRole('button', { name: 'Create ticket' }).click();
    await expect(t.getByText(`${label} is required`)).toBeVisible(); // blocked until filled
    await t.getByLabel(`${label} *`).fill('LT-' + key);
    await t.getByRole('button', { name: 'Create ticket' }).click();
    await expect(page.getByRole('heading', { name: 'Custom field ' + key })).toBeVisible();
    await expect(page.getByText('LT-' + key)).toBeVisible();
  } finally {
    // clean up: deactivate the field so later runs and other specs are unaffected
    const { api } = await admin();
    const all = await (await api.get('/api/v1/custom-fields/all')).json();
    for (const f of all.filter((x: { fieldKey: string; active: boolean }) => x.fieldKey === key && x.active)) await api.delete(`/api/v1/custom-fields/${f.id}`);
  }
});

test('configuration tab is kept in the address and survives a reload', async ({ page }) => {
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/settings');
  await page.getByRole('tab', { name: 'Notification rules' }).click();
  await expect(page).toHaveURL(/tab=rules/);
  await page.reload();
  await expect(page.getByRole('tab', { name: 'Notification rules' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('In-app notifications are always sent.')).toBeVisible();
});
