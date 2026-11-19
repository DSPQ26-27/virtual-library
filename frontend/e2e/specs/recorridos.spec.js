import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const password = 'ClaveE2E123!';
const email = () => `e2e-${randomUUID()}@example.com`;

async function registrar(page, correo) {
  await page.goto('/registro');
  await page.getByLabel('Nombre').fill('Estudiante E2E');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByText('Tu cuenta se ha creado correctamente.')).toBeVisible();
}
async function login(page, correo) {
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Catálogo' })).toBeVisible();
}
async function buscar(page, titulo) {
  await page.goto('/catalogo');
  await page.getByLabel('Buscar por título o autor').fill(titulo);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  const card = page.getByRole('article').filter({ has: page.getByRole('heading', { name: titulo, exact: true }) });
  await expect(card).toBeVisible();
  return card;
}
async function prestar(page, titulo) {
  const card = await buscar(page, titulo);
  await card.getByRole('button', { name: `Pedir préstamo de «${titulo}»` }).click();
  await card.getByRole('button', { name: 'Sí, pedir' }).click();
  await expect(page.getByText(/Préstamo confirmado/)).toBeVisible();
}

test('registro, credenciales, sesión y salida', async ({ page }) => {
  const correo = email();
  await page.goto('/registro');
  await page.getByLabel('Nombre').fill('  ');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('alert')).toContainText('El nombre debe tener');
  await registrar(page, correo);
  await page.getByLabel('Nombre').fill('Otra persona');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('alert')).toContainText(/correo|registrado/i);
  await login(page, correo);
  await expect(page.getByRole('link', { name: 'Mis préstamos' })).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('link', { name: 'Crear cuenta' })).toBeVisible();
  await page.goto('/mis-prestamos');
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
});

test('catálogo real permite filtrar y paginar', async ({ page }) => {
  const correo = email(); await registrar(page, correo); await login(page, correo);
  await page.getByLabel('Buscar por título o autor').fill('E2E Pagina');
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Página 1 de 2' })).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Página 2 de 2' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'E2E Pagina 25' })).toBeVisible();
  await page.getByLabel('Categoría').selectOption({ label: 'Categoria E2E' });
  await expect(page.getByText(/Categoría seleccionada: Categoria E2E/)).toBeVisible();
});

test('préstamo, dos renovaciones y devolución conservan el historial', async ({ page }) => {
  const correo = email(); await registrar(page, correo); await login(page, correo);
  await prestar(page, 'E2E Prestamo');
  await page.getByRole('link', { name: 'Mis préstamos', exact: true }).click();
  const card = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'E2E Prestamo' }) });
  await expect(card).toContainText('ACTIVO');
  await expect(card).toContainText('Fecha de vencimiento:');
  let vencimiento = (await (await page.request.get('/api/prestamos/mios')).json()).activos.find(p => p.titulo === 'E2E Prestamo').fechaVencimiento;
  for (let n = 1; n <= 2; n++) {
    await card.getByRole('button', { name: 'Renovar préstamo «E2E Prestamo»' }).click();
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/renovacion'));
    await card.getByRole('button', { name: 'Sí, renovar' }).click();
    const respuesta = await response;
    expect(respuesta.status()).toBe(200);
    const datos = await respuesta.json();
    const esperado = new Date(`${vencimiento.slice(0, 10)}T12:00:00Z`);
    esperado.setUTCDate(esperado.getUTCDate() + 30);
    expect(datos.fechaVencimiento.slice(0, 10)).toBe(esperado.toISOString().slice(0, 10));
    vencimiento = datos.fechaVencimiento;
    await expect(card).toContainText(`Renovaciones utilizadas: ${n} de 2`);
  }
  await expect(card.getByRole('button', { name: 'Renovar préstamo «E2E Prestamo»' })).toBeDisabled();
  await card.getByRole('button', { name: 'Devolver «E2E Prestamo»' }).click();
  await card.getByRole('button', { name: 'Sí, devolver' }).click();
  await expect(page.getByText(/Devolución de «E2E Prestamo» registrada/)).toBeVisible();
  await expect(card).toContainText('DEVUELTO');
  await expect(card.getByRole('button', { name: /Devolver/ })).toHaveCount(0);
});

test('devolución asigna FIFO y permite cancelar otra espera', async ({ browser }) => {
  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext()]);
  try {
    const [a, b, c] = await Promise.all(contexts.map(context => context.newPage()));
    for (const page of [a, b, c]) { const correo = email(); await registrar(page, correo); await login(page, correo); }
    await prestar(a, 'E2E FIFO');
    for (const page of [b, c]) {
      const card = await buscar(page, 'E2E FIFO');
      await expect(card).toContainText('Sin ejemplares disponibles');
      await expect(card.getByRole('button', { name: /Pedir préstamo/ })).toHaveCount(0);
      await card.getByRole('button', { name: 'Apuntarme a la lista de espera de «E2E FIFO»' }).click();
      await expect(card.getByText(/Te has apuntado/)).toBeVisible();
    }
    await a.goto('/mis-prestamos');
    const card = a.getByRole('article').filter({ has: a.getByRole('heading', { name: 'E2E FIFO' }) });
    await card.getByRole('button', { name: 'Devolver «E2E FIFO»' }).click();
    await card.getByRole('button', { name: 'Sí, devolver' }).click();
    await expect(a.getByText(/Devolución de «E2E FIFO» registrada/)).toBeVisible();
    await b.goto('/mis-reservas'); await expect(b.getByText('Estado: ASIGNADA')).toBeVisible();
    await b.goto('/mis-prestamos'); await expect(b.getByRole('article').filter({ hasText: 'E2E FIFO' })).toContainText('ACTIVO');
    await c.goto('/mis-reservas'); await expect(c.getByText('Estado: PENDIENTE')).toBeVisible();
    await c.getByRole('button', { name: 'Cancelar reserva de «E2E FIFO»' }).click();
    await expect(c.getByText('Estado: CANCELADA')).toBeVisible();
  } finally { await Promise.all(contexts.map(context => context.close())); }
});

test('personal gestiona libros y ejemplares; estudiante no puede administrar', async ({ browser }) => {
  const admin = await browser.newContext(); const student = await browser.newContext();
  try {
    const a = await admin.newPage(); const e = await student.newPage();
    const correoAdmin = email(); const correoEstudiante = email();
    await registrar(a, correoAdmin);
    const id = process.env.E2E_CONTAINER_ID;
    expect(id).toMatch(/^[a-f0-9]{64}$/);
    const etiqueta = execFileSync('docker', ['inspect', '--format', '{{index .Config.Labels "biblioteca.e2e"}}', id], { encoding: 'utf8' }).trim();
    expect(etiqueta).toBe(process.env.E2E_CONTAINER_LABEL);
    expect(etiqueta).toMatch(/^biblioteca-e2e-[a-f0-9-]+$/);
    execFileSync('docker', ['exec', '-i', id, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'e2e_user', '-d', 'biblioteca_e2e'], {
      input: `UPDATE usuarios SET rol = 'ADMIN' WHERE email = '${correoAdmin}';`, encoding: 'utf8',
    });
    await login(a, correoAdmin);
    await a.getByRole('link', { name: 'Añadir libro' }).first().click();
    await expect(a).toHaveURL(/\/admin\/libros\/nuevo$/);
    await expect(a.getByRole('heading', { name: 'Añadir libro' })).toBeVisible();
    await a.getByLabel('Título').fill('E2E Administracion');
    await a.getByLabel('Autor').fill('Autora E2E');
    await expect(a.getByLabel('Título')).toHaveValue('E2E Administracion');
    await expect(a.getByLabel('Autor')).toHaveValue('Autora E2E');
    await a.getByRole('button', { name: 'Crear libro' }).click();
    await expect(a.getByText('Libro «E2E Administracion» creado correctamente.')).toBeVisible();
    let card = await buscar(a, 'E2E Administracion');
    await card.getByRole('link', { name: 'Editar «E2E Administracion»' }).click();
    await expect(a).toHaveURL(/\/admin\/libros\/\d+\/editar$/);
    await expect(a.getByRole('heading', { name: 'Editar libro' })).toBeVisible();
    await a.getByLabel(/^Autor/).fill('Autora Editada');
    await expect(a.getByLabel(/^Autor/)).toHaveValue('Autora Editada');
    const actualizacion = a.waitForRequest(request => request.method() === 'PUT' && /\/api\/libros\/\d+$/.test(new URL(request.url()).pathname));
    await a.getByRole('button', { name: 'Guardar cambios' }).click();
    expect((await actualizacion).postDataJSON().autor).toBe('Autora Editada');
    await expect(a.getByText('Cambios guardados correctamente.')).toBeVisible();
    card = await buscar(a, 'E2E Administracion');
    await expect(card).toContainText('Autora Editada');
    await card.getByRole('link', { name: 'Ejemplares' }).click();
    await a.getByLabel('Código del ejemplar').fill('E2E-ADMIN-COPIA');
    await a.getByRole('button', { name: 'Registrar ejemplar' }).click();
    await expect(a.getByRole('heading', { name: 'Ejemplar E2E-ADMIN-COPIA' })).toBeVisible();
    await a.getByRole('button', { name: 'Retirar ejemplar E2E-ADMIN-COPIA' }).click();
    await a.getByRole('button', { name: 'Sí, retirar' }).click();
    await expect(a.getByText('Ejemplar «E2E-ADMIN-COPIA» retirado correctamente.')).toBeVisible();
    card = await buscar(a, 'E2E Administracion');
    await card.getByRole('button', { name: 'Retirar «E2E Administracion»' }).click();
    await card.getByRole('button', { name: 'Sí, retirar' }).click();
    await expect(a.getByText('«E2E Administracion» se ha retirado del catálogo.')).toBeVisible();
    await registrar(e, correoEstudiante); await login(e, correoEstudiante);
    await e.goto('/admin/libros/nuevo');
    await expect(e.getByRole('heading', { name: 'No tienes permiso' })).toBeVisible();
  } finally { await admin.close(); await student.close(); }
});
