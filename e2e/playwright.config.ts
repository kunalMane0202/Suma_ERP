import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests against a running stack:
 *   backend  http://localhost:8080 (SUMA_DEMO_DATA=true, SUMA_LOGIN_RATE_MAX raised for test logins)
 *   frontend http://localhost:5173 (vite dev server proxying /api)
 */
export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.SUMA_WEB ?? 'http://localhost:5173',
    viewport: { width: 1440, height: 900 },
    trace: 'off',
    screenshot: 'only-on-failure',
  },
});
