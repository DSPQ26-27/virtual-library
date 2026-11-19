import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/browser',
  outputDir: './test-results/browser',
  timeout: 90_000,
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:5179', browserName: 'chromium', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
});
