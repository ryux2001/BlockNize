import { expect, test } from '@playwright/test';

const email = process.env.BLOCKNIZE_ACCEPTANCE_EMAIL;
const password = process.env.BLOCKNIZE_ACCEPTANCE_PASSWORD;

test('web crea una semana y un bloque compartidos con los otros clientes', async ({ page }) => {
  test.skip(!email || !password, 'Carga las credenciales sintéticas desde backend/.env.');
  const weekName = 'H5 Cross-client acceptance';
  const blockName = 'Bloque H5 compartido';

  await page.goto('/iniciar-sesion');
  await page.getByLabel('Correo electrónico').fill(email!);
  await page.getByLabel('Contraseña').fill(password!);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL('**/hoy');

  await page.goto('/semanas');
  const templatesResponse = await page.request.get('/api/backend/templates');
  expect(templatesResponse.ok()).toBeTruthy();
  const templates = await templatesResponse.json() as Array<{ id: string; name: string }>;
  const existing = templates.find((template) =>
    template.name.startsWith('H5 Cross-client ') || template.name === 'H5 Desktop roundtrip');
  if (existing) {
    await page.getByRole('button', { name: existing.name }).click();
    await expect(page.getByRole('heading', { name: existing.name })).toBeVisible();
  } else {
    await page.getByRole('button', { name: 'Nueva semana' }).click();
    await page.getByLabel('Nombre de la semana').fill(weekName);
    await page.getByRole('button', { name: 'Crear semana' }).click();
    await expect(page.getByRole('heading', { name: weekName })).toBeVisible();
  }

  if (!(await page.getByText(new RegExp(blockName)).count())) {
    await page.getByRole('button', { name: 'Añadir bloque' }).click();
    await page.getByLabel('Nombre del bloque').fill(blockName);
    await page.getByLabel('Etiqueta (opcional)').fill('Aceptación H5');
    await page.getByLabel('Descripción (opcional)').fill('Creado desde la web para comprobar la convergencia entre clientes.');
    await page.getByRole('button', { name: 'Crear bloque' }).click();
  }
  await expect(page.getByText(new RegExp(blockName))).toBeVisible();

  if ((existing?.name ?? weekName) !== weekName) {
    await page.getByRole('button', { name: 'Cambiar nombre' }).click();
    await page.getByLabel('Nuevo nombre').fill(weekName);
    await page.getByRole('button', { name: 'Guardar nombre' }).click();
    await expect(page.getByRole('heading', { name: weekName })).toBeVisible();
  }

  if (await page.getByRole('button', { name: 'Usar esta semana' }).count()) {
    await page.getByRole('button', { name: 'Usar esta semana' }).click();
  }
  await expect(page.getByText('Activa', { exact: true })).toBeVisible();
});
