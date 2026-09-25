import {defineConfig} from '@playwright/test';

export default defineConfig({
  testDir: './shell/test',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  outputDir: '../build/playwright-results',
  reporter: process.env.CI
    ? [['github'], ['html', {open: 'never', outputFolder: '../build/reports/playwright'}]]
    : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    trace: 'retain-on-failure'
  },
  webServer: {
    command: 'node shell/test/server.mjs',
    url: 'http://127.0.0.1:4173/health',
    reuseExistingServer: !process.env.CI
  }
});
