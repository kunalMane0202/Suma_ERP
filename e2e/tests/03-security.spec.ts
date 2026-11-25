import { expect, request, test } from '@playwright/test';
import { ADMIN, admin, apiAs, createEmployee, PASSWORD, uiLogin, WEB } from './helpers';

test('14-16 successful, failed and unknown-user sign-ins are recorded with IP', async ({ page }) => {
  const { api } = await admin();
  const emp = await createEmployee(api, 'L1');
  const anon = await request.newContext({ baseURL: WEB });
  expect((await anon.post('/api/v1/auth/login', { data: { username: emp.username, password: 'Wrong-pass1' } })).status()).toBe(401);
  const ghost = 'ghost-' + Date.now();
  expect((await anon.post('/api/v1/auth/login', { data: { username: ghost, password: 'Whatever-1' } })).status()).toBe(401);
  await apiAs(emp.username, PASSWORD);

  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/security');
  await page.getByRole('tab', { name: 'Sign-ins' }).click();
  await page.getByLabel('Filter by username').fill(emp.username);
  const rows = page.getByRole('row').filter({ hasText: emp.username });
  await expect(rows.filter({ hasText: 'Success' })).toHaveCount(1);
  await expect(rows.filter({ hasText: 'Invalid credentials' })).toHaveCount(1);
  await expect(rows.first()).toContainText(/127\.0\.0\.1|::1/);
  await page.getByLabel('Filter by username').fill(ghost);
  await expect(page.getByRole('row').filter({ hasText: ghost }).filter({ hasText: 'Unknown user' })).toHaveCount(1);
});

test('17 unauthenticated and unauthorized requests are monitored', async ({ page }) => {
  const { api } = await admin();
  const emp = await createEmployee(api, 'L1');
  const anon = await request.newContext({ baseURL: WEB });
  expect((await anon.get('/api/v1/tickets')).status()).toBe(401);
  const l1 = await apiAs(emp.username, PASSWORD);
  expect((await l1.api.get('/api/v1/security/dashboard')).status()).toBe(403);

  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/security');
  await page.getByRole('tab', { name: 'Unauthenticated' }).click();
  await expect(page.getByRole('row').filter({ hasText: '/api/v1/tickets' }).filter({ hasText: 'Missing auth' }).first()).toBeVisible();
  await page.getByLabel('Category').selectOption('ACCESS_DENIED');
  await expect(page.getByRole('row').filter({ hasText: '/api/v1/security/dashboard' }).filter({ hasText: emp.username }).first()).toBeVisible();
});

test('18 admin security dashboard shows sign-in statistics', async ({ page }) => {
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/security');
  for (const label of ['Successful sign-ins', 'Failed sign-ins', 'Unknown usernames', 'Unauthenticated requests', 'Unique IP addresses', 'Suspicious events', 'Active sessions']) {
    await expect(page.getByText(label, { exact: true }).first()).toBeVisible();
  }
  await expect(page.getByText('Most active IP addresses')).toBeVisible();
});

test('19 administrator terminates a session and the employee is signed out', async ({ browser }) => {
  const { api } = await admin();
  const emp = await createEmployee(api, 'L1');
  const empCtx = await browser.newContext();
  const empPage = await empCtx.newPage();
  await uiLogin(empPage, emp.username, PASSWORD);
  await empPage.goto('/attendance');
  await expect(empPage.getByRole('heading', { name: 'My attendance' })).toBeVisible();

  const adminCtx = await browser.newContext();
  const adminPage = await adminCtx.newPage();
  await uiLogin(adminPage, ADMIN.username, ADMIN.password);
  await adminPage.goto('/security');
  await adminPage.getByRole('tab', { name: 'Active sessions' }).click();
  const row = adminPage.getByRole('row').filter({ hasText: emp.fullName }).first();
  await row.getByRole('button', { name: 'Terminate' }).click();
  await adminPage.getByLabel('Reason (recorded in the audit trail)').fill('E2E termination');
  await adminPage.getByRole('button', { name: 'Terminate session' }).click();
  await expect(adminPage.getByText(/terminated/).first()).toBeVisible();

  await empPage.waitForTimeout(21_000); // session validation cache window
  await empPage.goto('/tickets');
  await expect(empPage).toHaveURL(/\/login/);
});

test('20 role permissions: engineers cannot reach administration', async ({ page }) => {
  const { api } = await admin();
  const emp = await createEmployee(api, 'L1');
  await uiLogin(page, emp.username, PASSWORD);
  const nav = page.getByRole('navigation', { name: 'Main' });
  await expect(nav.getByRole('link', { name: 'Tickets' })).toBeVisible();
  for (const hidden of ['Login security', 'Audit trail', 'Configuration', 'Automation', 'Integrations', 'Clients', 'Team attendance']) {
    await expect(nav.getByRole('link', { name: hidden })).toHaveCount(0);
  }
  await page.goto('/security');
  await expect(page.getByRole('heading', { name: "You don't have access to this page" })).toBeVisible();
  const l1 = await apiAs(emp.username, PASSWORD);
  expect((await l1.api.get('/api/v1/users')).status()).toBe(403);
  expect((await l1.api.get('/api/v1/audit')).status()).toBe(403);
});

test('ticket lifecycle from the UI', async ({ page }) => {
  await uiLogin(page, ADMIN.username, ADMIN.password);
  await page.goto('/tickets');
  await page.getByRole('button', { name: 'New ticket' }).click();
  const d = page.getByRole('dialog', { name: 'New ticket' });
  await d.getByLabel('Project').selectOption({ label: 'Acme ERP Support' });
  await d.getByLabel('Priority').selectOption('HIGH');
  const title = 'E2E printer outage ' + Date.now();
  await d.getByLabel('Title').fill(title);
  await d.getByRole('button', { name: 'Create ticket' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
  await expect(page.getByText('On track').first()).toBeVisible();
  await expect(page.getByText('Activity timeline')).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'Start work' }).click();
  await expect(page.getByRole('dialog', { name: 'Move to in progress' })).toBeVisible();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('In progress').first()).toBeVisible();
  await page.getByRole('button', { name: 'Resolve' }).click();
  await page.getByRole('dialog').getByLabel('Note (required)').fill('Replaced the toner');
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('Resolved').first()).toBeVisible();
  await expect(page.getByText('[RESOLVED] Replaced the toner')).toBeVisible();
});
