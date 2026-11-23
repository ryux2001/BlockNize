import { defineConfig, devices } from '@playwright/test';

const baseURL = 'http://127.0.0.1:3000';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'unit', testMatch: '**/*.unit.spec.ts' },
    {
      name: 'chromium',
      testMatch: '**/*.e2e.spec.ts',
      use: { ...devices['Desktop Chrome'], browserName: 'chromium' },
    },
    {
      name: 'firefox',
      testMatch: '**/*.e2e.spec.ts',
      use: { ...devices['Desktop Firefox'], browserName: 'firefox' },
    },
    {
      name: 'webkit',
      testMatch: '**/*.e2e.spec.ts',
      use: { ...devices['Desktop Safari'], browserName: 'webkit' },
    },
    {
      name: 'mobile-chromium',
      testMatch: '**/*.e2e.spec.ts',
      use: { ...devices['Pixel 7'], browserName: 'chromium' },
    },
    {
      name: 'integration',
      testMatch: '**/*.integration.spec.ts',
      use: { ...devices['Desktop Chrome'], browserName: 'chromium', trace: 'off', screenshot: 'off', video: 'off' },
    },
  ],
  webServer: {
    command: 'npm run dev -- --hostname 127.0.0.1',
    url: `${baseURL}/iniciar-sesion`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
