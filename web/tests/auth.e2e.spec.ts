import { expect, test } from '@playwright/test';

test('sign-in page exposes labeled email and password fields', async ({ page }) => {
  await page.goto('/iniciar-sesion');

  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toHaveAttribute('type', 'email');
  await expect(page.getByLabel('Contraseña')).toHaveAttribute('autocomplete', 'current-password');
  await expect(page.getByRole('link', { name: 'Crear cuenta' })).toHaveAttribute('href', '/crear-cuenta');
});

test('sign-up page asks for a name and marks account fields as required', async ({ page }) => {
  await page.goto('/crear-cuenta');

  await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible();
  await expect(page.getByLabel('Nombre')).toHaveAttribute('required', '');
  await expect(page.getByLabel('Correo electrónico')).toHaveAttribute('required', '');
  await expect(page.getByLabel('Contraseña')).toHaveAttribute('minlength', '8');
});

test('mobile sign-in has no horizontal overflow', async ({ page }) => {
  await page.goto('/iniciar-sesion');

  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport);
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
});

test('empty sign-in is blocked by the browser before sending a request', async ({ page }) => {
  const authRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/auth/')) authRequests.push(request.url());
  });
  await page.goto('/iniciar-sesion');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await expect(page.getByLabel('Correo electrónico')).toBeFocused();
  expect(authRequests).toHaveLength(0);
});
