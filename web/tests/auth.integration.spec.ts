import { expect, test } from '@playwright/test';

const email = process.env.BLOCKNIZE_ACCEPTANCE_EMAIL;
const password = process.env.BLOCKNIZE_ACCEPTANCE_PASSWORD;

test('Neon session authenticates the same account in NestJS and closes cleanly', async ({ page }) => {
  test.skip(!email || !password, 'Load the synthetic Neon acceptance credentials from backend/.env.');
  test.setTimeout(75_000);
  const failedApiResponses: string[] = [];
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname;
    if (response.status() >= 400) {
      failedApiResponses.push(`${path}:${response.status()}`);
    }
  });

  await page.goto('/iniciar-sesion');
  await page.getByLabel('Correo electrónico').fill(email!);
  await page.getByLabel('Contraseña').fill(password!);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.getByLabel('Contraseña').fill('');
  const authMessage = page.locator('.auth-message[role="alert"]');
  const outcome = await Promise.race([
    page.waitForURL('**/hoy', { timeout: 60_000 }).then(() => 'authenticated').catch(() => 'timeout'),
    authMessage.waitFor({ state: 'visible', timeout: 60_000 }).then(() => 'rejected').catch(() => 'timeout'),
  ]);
  if (outcome === 'rejected') {
    const message = await authMessage.innerText();
    throw new Error(`Authentication flow failed: ${message}. API responses: ${failedApiResponses.join(', ') || 'none'}.`);
  }
  expect(outcome, `The session did not reach /hoy. API responses: ${failedApiResponses.join(', ') || 'none'}. URL: ${page.url()}`).toBe('authenticated');
  await expect(page.getByRole('heading', { name: 'Tu día' })).toBeVisible();

  await page.goto('/cuenta');
  await expect(page.getByRole('main').getByText(email!, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL('**/iniciar-sesion');
});
