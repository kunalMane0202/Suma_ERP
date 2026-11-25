import { expect, type APIRequestContext, type Page, request } from '@playwright/test';

export const WEB = process.env.SUMA_WEB ?? 'http://localhost:5173';
export const ADMIN = { username: 'admin', password: process.env.SUMA_ADMIN_PASSWORD ?? 'Admin@12345' };
export const PASSWORD = 'E2e-Password1';

export async function apiAs(username: string, password: string): Promise<{ api: APIRequestContext; token: string; me: any }> {
  const ctx = await request.newContext({ baseURL: WEB });
  const r = await ctx.post('/api/v1/auth/login', { data: { username, password } });
  expect(r.status(), await r.text()).toBe(200);
  const body = await r.json();
  const api = await request.newContext({ baseURL: WEB, extraHTTPHeaders: { Authorization: `Bearer ${body.accessToken}` } });
  return { api, token: body.accessToken, me: body.user };
}

export async function admin() {
  return apiAs(ADMIN.username, ADMIN.password);
}

let seq = 0;
export async function createEmployee(api: APIRequestContext, role: string, projectCode = 'ACME-ERP') {
  const id = `${Date.now().toString(36)}${seq++}`;
  const r = await api.post('/api/v1/users', {
    data: { employeeCode: `E2E-${id}`.toUpperCase(), username: `e2e.${id}`, email: `e2e.${id}@demo.suma.local`, fullName: `E2E ${role} ${id}`,
      role, password: PASSWORD, status: 'ACTIVE', weeklyOffs: [] },
  });
  expect(r.status(), await r.text()).toBe(201);
  const user = await r.json();
  const projects = await (await api.get('/api/v1/projects', { params: { q: projectCode } })).json();
  const project = projects.content.find((p: any) => p.code === projectCode);
  await api.put(`/api/v1/projects/${project.id}/members`, { data: { userId: user.id, designation: 'MEMBER', allocationPercent: 100, primary: true } });
  return { ...user, project };
}

export async function uiLogin(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Username or email').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}
