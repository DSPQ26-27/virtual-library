// Prueba funcional real: React + Spring Boot + PostgreSQL. No simula HTTP.
// Requiere la base temporal biblioteca_us09_e2e y Playwright del entorno.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";

const baseUrl = process.env.US09_BASE_URL || "http://127.0.0.1:5177";
const container = process.env.US09_DB_CONTAINER || "biblioteca-us09-e2e";
assert.ok(["127.0.0.1", "localhost"].includes(new URL(baseUrl).hostname), "Usar una aplicación local de pruebas");
assert.match(container, /^biblioteca-us09-[a-z0-9-]+$/, "Usar un contenedor de pruebas de US09");

function sql(statement) {
  return execFileSync("docker", ["exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
    "-U", "us09_e2e", "-d", "biblioteca_us09_e2e"], { input: statement, encoding: "utf8", timeout: 15000 }).trim();
}
assert.equal(sql("SELECT current_database();"), "biblioteca_us09_e2e");
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const browser = await chromium.launch({ headless: true,
  ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}) });
const contexts = [];
const books = [];
const users = [];
const tag = randomUUID().slice(0, 8);
const password = randomUUID();
const report = { database: "PostgreSQL", version: sql("SHOW server_version;"), checks: [] };
let sequence = 0;

async function check(name, action) {
  await action();
  report.checks.push(name);
  console.log(`OK: ${name}`);
}

async function context() {
  const created = await browser.newContext({ baseURL: baseUrl });
  contexts.push(created);
  return created;
}

async function post(client, path, data) {
  const csrfResponse = await client.get("/api/auth/csrf");
  assert.equal(csrfResponse.status(), 200);
  const { token } = await csrfResponse.json();
  const response = await client.post(path, { headers: { "X-CSRF-TOKEN": token }, ...(data ? { data } : {}) });
  return { status: response.status(), body: await response.json() };
}

async function register(client, name) {
  const email = `us09-${tag}-${name}@example.com`;
  const response = await post(client, "/api/auth/register", { nombre: `US09 ${name}`, email, password });
  assert.equal(response.status, 201);
  users.push(response.body.id);
  return { ...response.body, email };
}

async function login(client, account) {
  const result = await post(client, "/api/auth/login", { email: account.email, password });
  assert.equal(result.status, 200);
}

function loanRow(id) {
  return JSON.parse(sql(`SELECT row_to_json(t) FROM (
    SELECT id, estado, numero_renovaciones, fecha_prestamo, fecha_vencimiento, fecha_devolucion,
      EXTRACT(EPOCH FROM (fecha_vencimiento - fecha_prestamo)) / 86400 AS dias
    FROM prestamos WHERE id = ${id}) t;`));
}

async function reject(client, loan, code) {
  const before = loanRow(loan.id);
  const response = await post(client, `/api/prestamos/${loan.id}/renovacion`);
  assert.equal(response.status, code === "PRESTAMO_NO_ENCONTRADO" ? 404 : 409);
  assert.equal(response.body.codigo, code);
  assert.deepEqual(loanRow(loan.id), before, "Un rechazo no debe cambiar la fila de PostgreSQL");
}

try {
  const student = await context();
  const other = await context();
  const staff = await context();
  const account = await register(student.request, "estudiante");
  const otherAccount = await register(other.request, "otro");
  const staffAccount = await register(staff.request, "personal");
  sql(`UPDATE usuarios SET rol = 'ADMIN' WHERE id = ${staffAccount.id};`);
  await login(other.request, otherAccount);
  await login(staff.request, staffAccount);

  const page = await student.newPage();
  page.setDefaultTimeout(15000);
  const errors = [];
  const writes = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/renovacion")) writes.push(new URL(request.url()).pathname);
  });
  const card = (title) => page.getByRole("article").filter({ has: page.getByRole("heading", { name: title, exact: true }) });
  const renewButton = (title) => card(title).getByRole("button", { name: `Renovar préstamo «${title}»`, exact: true });

  async function copy(bookId) {
    const result = await post(staff.request, `/api/libros/${bookId}/ejemplares`,
      { codigo: `US09-${tag}-${++sequence}`, estado: "DISPONIBLE" });
    assert.equal(result.status, 201);
    return result.body;
  }

  async function borrow(title, client = student.request) {
    const book = await post(staff.request, "/api/libros", { titulo: `US09 ${tag} ${title}`, autor: "Prueba US09" });
    assert.equal(book.status, 201);
    books.push(book.body.id);
    await copy(book.body.id);
    const result = await post(client, "/api/prestamos", { libroId: book.body.id });
    assert.equal(result.status, 201);
    return result.body;
  }

  async function returnLoan(loan, client = student.request) {
    const result = await post(client, `/api/prestamos/${loan.id}/devolucion`);
    assert.equal(result.status, 200);
  }

  async function renewOnScreen(loan, code) {
    await page.goto(`${baseUrl}/mis-prestamos`);
    await renewButton(loan.titulo).click();
    await page.getByText(`¿Renovar «${loan.titulo}» por 30 días?`, { exact: true }).waitFor();
    const before = loanRow(loan.id);
    const previousWrites = writes.length;
    const completed = page.waitForResponse((response) => response.request().method() === "POST" &&
      new URL(response.url()).pathname === `/api/prestamos/${loan.id}/renovacion`);
    // Dos clics reales sobre el mismo botón deben producir un solo POST.
    await card(loan.titulo).getByRole("button", { name: "Sí, renovar", exact: true })
      .evaluate((button) => { button.click(); button.click(); });
    const response = await completed;
    const body = await response.json();
    assert.equal(response.status(), code ? 409 : 200);
    if (code) {
      assert.equal(body.codigo, code);
      await page.getByRole("alert").filter({ hasText: body.message }).waitFor();
      assert.deepEqual(loanRow(loan.id), before);
    } else {
      await card(loan.titulo).getByText(`Renovaciones utilizadas: ${body.numeroRenovaciones} de 2`, { exact: true }).waitFor();
      await card(loan.titulo).getByText(`Renovaciones restantes: ${2 - body.numeroRenovaciones}`, { exact: true }).waitFor();
      const date = body.fechaVencimiento.slice(0, 10).split("-").reverse().join("/");
      await card(loan.titulo).getByText(`Fecha de vencimiento: ${date}`, { exact: true }).waitFor();
    }
    assert.equal(writes.length, previousWrites + 1);
    return body;
  }

  await check("Registro por API e inicio de sesión desde React", async () => {
    await page.goto(`${baseUrl}/login`);
    await page.getByLabel("Correo electrónico", { exact: true }).fill(account.email);
    await page.getByLabel("Contraseña", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Iniciar sesión", exact: true }).click();
    await page.getByRole("link", { name: "Mis préstamos", exact: true }).waitFor();
    assert.equal((await student.request.get("/api/auth/me")).status(), 200);
  });

  const valid = await borrow("Renovación válida");
  await check("Confirmación y cancelación no envían la renovación", async () => {
    await page.goto(`${baseUrl}/mis-prestamos`);
    await renewButton(valid.titulo).click();
    await card(valid.titulo).getByRole("button", { name: "Cancelar", exact: true }).click();
    assert.equal(writes.length, 0);
    assert.equal(loanRow(valid.id).numero_renovaciones, 0);
  });
  await check("Primera renovación desde React: contador 1 y 30 días adicionales en PostgreSQL", async () => {
    assert.equal(loanRow(valid.id).dias, 30);
    await renewOnScreen(valid);
    assert.equal(loanRow(valid.id).numero_renovaciones, 1);
    assert.equal(loanRow(valid.id).dias, 60);
  });
  await check("Segunda renovación: contador 2 y otros 30 días; tercera rechazada", async () => {
    await renewOnScreen(valid);
    assert.equal(loanRow(valid.id).numero_renovaciones, 2);
    assert.equal(loanRow(valid.id).dias, 90);
    assert.ok(await renewButton(valid.titulo).isDisabled());
    await reject(student.request, valid, "LIMITE_RENOVACIONES");
  });
  await check("Renovación de un préstamo devuelto rechazada sin modificar PostgreSQL", async () => {
    await returnLoan(valid);
    await reject(student.request, valid, "PRESTAMO_NO_ACTIVO");
  });

  await check("Préstamo vencido rechazado y motivo visible en React", async () => {
    const loan = await borrow("Vencido");
    sql(`UPDATE prestamos SET fecha_prestamo = CURRENT_TIMESTAMP - INTERVAL '60 days',
      fecha_vencimiento = CURRENT_TIMESTAMP - INTERVAL '30 days' WHERE id = ${loan.id};`);
    await renewOnScreen(loan, "PRESTAMO_VENCIDO");
    await returnLoan(loan);
  });
  await check("Préstamo cancelado rechazado sin modificar PostgreSQL", async () => {
    const loan = await borrow("Cancelado");
    sql(`BEGIN; UPDATE prestamos SET estado = 'CANCELADO' WHERE id = ${loan.id};
      UPDATE ejemplares SET estado = 'DISPONIBLE' WHERE id = (SELECT ejemplar_id FROM prestamos WHERE id = ${loan.id}); COMMIT;`);
    await reject(student.request, loan, "PRESTAMO_NO_ACTIVO");
  });
  await check("Estudiante bloqueado por otro préstamo vencido: rechazo visible en React", async () => {
    const target = await borrow("Estudiante bloqueado");
    const overdue = await borrow("Origen del bloqueo");
    sql(`UPDATE prestamos SET fecha_prestamo = CURRENT_TIMESTAMP - INTERVAL '60 days',
      fecha_vencimiento = CURRENT_TIMESTAMP - INTERVAL '30 days' WHERE id = ${overdue.id};`);
    await renewOnScreen(target, "ESTUDIANTE_BLOQUEADO");
    await returnLoan(overdue);
    await returnLoan(target);
  });
  await check("Cuenta inactiva no puede renovar con una sesión ya iniciada", async () => {
    const loan = await borrow("Cuenta inactiva");
    sql(`UPDATE usuarios SET activo = false WHERE id = ${account.id};`);
    try { await reject(student.request, loan, "ESTUDIANTE_BLOQUEADO"); }
    finally { sql(`UPDATE usuarios SET activo = true WHERE id = ${account.id};`); }
    await returnLoan(loan);
  });
  await check("Un estudiante no ve ni renueva préstamos de otro usuario", async () => {
    const loan = await borrow("Préstamo ajeno", other.request);
    await reject(student.request, loan, "PRESTAMO_NO_ENCONTRADO");
    await page.goto(`${baseUrl}/mis-prestamos`);
    await page.getByText("No tienes préstamos activos.", { exact: true }).waitFor();
    assert.equal(await card(loan.titulo).count(), 0);
    await returnLoan(loan, other.request);
  });

  const reserved = await borrow("Lista de espera");
  sql(`INSERT INTO solicitudes_espera (libro_id, usuario_id) VALUES (${reserved.libroId}, ${otherAccount.id});`);
  await check("Reserva pendiente sin copias libres: rechazo en React y datos intactos", async () => {
    await renewOnScreen(reserved, "LISTA_ESPERA_PENDIENTE");
  });
  await check("Reserva pendiente con otra copia libre: renovación permitida", async () => {
    await copy(reserved.libroId);
    await renewOnScreen(reserved);
    assert.equal(loanRow(reserved.id).numero_renovaciones, 1);
    assert.equal(loanRow(reserved.id).dias, 60);
    assert.equal(sql(`SELECT COUNT(*) FROM solicitudes_espera WHERE libro_id = ${reserved.libroId} AND estado = 'PENDIENTE';`), "1");
    await returnLoan(reserved);
  });

  const simultaneous = await borrow("Renovaciones simultáneas");
  await check("Tres peticiones HTTP simultáneas: dos correctas y una rechazada", async () => {
    const responses = await Promise.all([1, 2, 3].map(() => post(student.request, `/api/prestamos/${simultaneous.id}/renovacion`)));
    assert.deepEqual(responses.map((response) => response.status).sort(), [200, 200, 409]);
    assert.equal(responses.find((response) => response.status === 409).body.codigo, "LIMITE_RENOVACIONES");
    assert.equal(loanRow(simultaneous.id).numero_renovaciones, 2);
    assert.equal(loanRow(simultaneous.id).dias, 90);
  });
  await check("Persistencia tras recargar React y abrir una sesión nueva", async () => {
    await page.goto(`${baseUrl}/mis-prestamos`);
    await card(simultaneous.titulo).getByText("Renovaciones utilizadas: 2 de 2", { exact: true }).waitFor();
    const fresh = await context();
    await login(fresh.request, account);
    const response = await fresh.request.get("/api/prestamos/mios");
    assert.equal(response.status(), 200);
    const persisted = (await response.json()).activos.find((loan) => loan.id === simultaneous.id);
    assert.equal(persisted.numeroRenovaciones, 2);
    assert.equal(persisted.fechaVencimiento.replace("T", " "), loanRow(simultaneous.id).fecha_vencimiento.replace("T", " "));
  });
  assert.deepEqual(errors, [], "El navegador no debe registrar errores de JavaScript");
} finally {
  // Solo se eliminan los registros creados por esta ejecución en la base temporal.
  if (books.length) {
    const ids = books.join(",");
    sql(`BEGIN; DELETE FROM solicitudes_espera WHERE libro_id IN (${ids});
      DELETE FROM prestamos WHERE ejemplar_id IN (SELECT id FROM ejemplares WHERE libro_id IN (${ids}));
      DELETE FROM ejemplares WHERE libro_id IN (${ids}); DELETE FROM libros WHERE id IN (${ids}); COMMIT;`);
  }
  if (users.length) sql(`DELETE FROM usuarios WHERE id IN (${users.join(",")});`);
  await Promise.all(contexts.map((item) => item.close()));
  await browser.close();
  if (process.env.US09_REPORT) await writeFile(process.env.US09_REPORT, JSON.stringify(report, null, 2));
}
console.log(`Prueba funcional completada: ${report.checks.length} comprobaciones correctas.`);
