import { expect, test } from '@playwright/test';
import { admin, createEmployee, PASSWORD, uiLogin } from './helpers';

let user: { username: string; fullName: string; id: string };

test.beforeAll(async () => {
  const { api } = await admin();
  user = await createEmployee(api, 'L1');
});

test('1-3 employee login creates an attendance session, light mode by default', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' }); // OS preference must be ignored
  await uiLogin(page, user.username, PASSWORD);
  await expect(page.locator('html')).not.toHaveClass(/dark/);
  await page.goto('/attendance');
  await expect(page.getByRole('heading', { name: 'My attendance' })).toBeVisible();
  await expect(page.getByText('Working', { exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'active' }).first()).toBeVisible(); // current session row
  await expect(page.getByText('First sign-in')).toBeVisible();
});

test('4-6 break start, end and duration', async ({ page }) => {
  await uiLogin(page, user.username, PASSWORD);
  await page.goto('/attendance');
  await page.getByLabel('Break type').selectOption('MEAL');
  await page.getByRole('button', { name: 'Start break' }).click();
  await expect(page.getByText('Break started').first()).toBeVisible();
  await expect(page.getByText(/^On break \d\d:\d\d:\d\d$/)).toBeVisible(); // top bar timer
  await page.waitForTimeout(2200);
  await page.getByRole('button', { name: 'End break' }).click();
  await expect(page.getByText(/^Break ended/).first()).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: 'Meal break' }).first();
  await expect(row).toBeVisible();
  await expect(row.getByRole('cell').nth(3)).toHaveText(/^[2-9]s$|^\d+s$/); // measured duration in seconds
  await expect(page.getByText(/Breaks \(1/)).toBeVisible();
});

test('7-8 inactivity prompt after 20 minutes and employee confirmation', async ({ page }) => {
  await page.clock.install();
  await uiLogin(page, user.username, PASSWORD);
  await page.goto('/attendance');
  await expect(page.getByRole('heading', { name: 'My attendance' })).toBeVisible();
  await page.clock.fastForward('19:00');
  await expect(page.getByRole('dialog')).toHaveCount(0); // not before the threshold
  await page.clock.fastForward('02:00');
  const dialog = page.getByRole('dialog', { name: 'Are you still working?' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText(/Only activity inside this SUMA tab/)).toBeVisible();
  await dialog.getByRole('button', { name: "I'm working" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText('You answered: Working')).toBeVisible();
});

test('2 logout ends the session and returns to sign-in', async ({ page }) => {
  await uiLogin(page, user.username, PASSWORD);
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto('/attendance');
  await expect(page).toHaveURL(/\/login/); // refresh cookie revoked
});

test('dark mode can be switched on and persists', async ({ page }) => {
  await uiLogin(page, user.username, PASSWORD);
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expect(page.locator('html')).toHaveClass(/dark/);
  await page.reload();
  await expect(page.locator('html')).toHaveClass(/dark/);
  await page.getByRole('button', { name: 'Switch to light mode' }).click();
  await expect(page.locator('html')).not.toHaveClass(/dark/);
});
