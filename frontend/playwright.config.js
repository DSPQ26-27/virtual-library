import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e/specs',
  outputDir: './test-results/e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: './playwright-report/e2e', open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL, browserName: 'chromium', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
});
