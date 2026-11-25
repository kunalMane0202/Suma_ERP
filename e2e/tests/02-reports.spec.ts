import { expect, test } from '@playwright/test';
import { ADMIN, admin, createEmployee, uiLogin } from './helpers';

test('9 daily attendance report lists the employee with exports', async ({ page }) => {
  const { api } = await admin();
  const emp = await createEmployee(api, 'L2');
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/attendance/team');
  await page.getByRole('tab', { name: 'Daily report' }).click();
  await page.getByLabel('Employee').selectOption({ label: emp.fullName }).catch(async () => {
    // directory cache may predate the new employee: reload and retry
    await page.reload(); await page.getByRole('tab', { name: 'Daily report' }).click();
    await page.getByLabel('Employee').selectOption({ label: emp.fullName });
  });
  await expect(page.getByRole('row').filter({ hasText: emp.fullName })).toBeVisible();
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Excel' }).click()]);
  expect(download.suggestedFilename()).toMatch(/^attendance-daily-\d{4}-\d{2}-\d{2}\.xlsx$/);
});

test('10 monthly attendance report shows employee totals', async ({ page }) => {
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/attendance/team');
  await page.getByRole('tab', { name: 'Monthly report' }).click();
  await expect(page.getByRole('columnheader', { name: 'Days present' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Neha Gupta' })).toBeVisible();
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'PDF' }).click()]);
  expect(download.suggestedFilename()).toMatch(/^attendance-monthly-\d{4}-\d{2}\.pdf$/);
});

test('11-13 Project Lead routing, duplicate prevention and failed delivery retry', async () => {
  test.setTimeout(180_000);
  const { api } = await admin();
  // project led by an employee whose account email = report email (auto-verified)
  const lead = await createEmployee(api, 'L3');
  const clients = await (await api.get('/api/v1/clients')).json();
  const code = 'E2E' + Date.now().toString(36).toUpperCase().slice(-6);
  const pr = await api.post('/api/v1/projects', { data: { clientId: clients.content[0].id, code, name: 'E2E ' + code, projectLeadId: lead.id, projectLeadEmail: lead.email, status: 'ACTIVE' } });
  expect(pr.status(), await pr.text()).toBe(201);
  const project = await pr.json();
  expect(project.projectLeadEmailVerified).toBe(true);
  const now = new Date();
  const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const body = { projectId: project.id, year: prev.getFullYear(), month: prev.getMonth() + 1 };
  const gen = await api.post('/api/v1/attendance/monthly-reports', { data: body });
  expect(gen.status(), await gen.text()).toBe(201);
  const log = await gen.json();
  expect(log.recipientEmail).toBe(lead.email);
  expect(log.status).toBe('GENERATED');
  // duplicate generation for the same project and month is refused
  const dup = await api.post('/api/v1/attendance/monthly-reports', { data: body });
  expect(dup.status()).toBe(409);
  // no SMTP server in this environment: the delivery job records a failed attempt and schedules a retry
  let current = log;
  for (let i = 0; i < 20 && current.attempts === 0; i++) {
    await new Promise((r) => setTimeout(r, 5000));
    current = await (await api.get(`/api/v1/attendance/monthly-reports/${log.id}`)).json();
  }
  expect(current.attempts).toBeGreaterThanOrEqual(1);
  expect(['RETRYING', 'SENT']).toContain(current.status);
  if (current.status === 'RETRYING') expect(current.nextRetryAt).toBeTruthy();
});
