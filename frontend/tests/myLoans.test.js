import test from "node:test";
import assert from "node:assert/strict";
import { getMyLoans, LoanError } from "../src/services/loans.js";

const activo = { id: 1, libroId: 2, titulo: "Libro", fechaPrestamo: "2026-11-01T10:00:00", fechaVencimiento: "2026-12-01T10:00:00", fechaDevolucion: null, estado: "ACTIVO" };
const devuelto = { ...activo, id: 3, fechaDevolucion: "2026-11-20T10:00:00", estado: "DEVUELTO" };

test("consulta préstamos e historial usando la sesión", async (t) => {
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/prestamos/mios");
    assert.equal(options.method, "GET"); assert.equal(options.credentials, "include");
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json({ activos: [activo], historial: [devuelto] });
  });
  assert.deepEqual(await getMyLoans(), { activos: [activo], historial: [devuelto] });
});

test("acepta listas vacías y rechaza respuestas inconsistentes", async (t) => {
  let body = { activos: [], historial: [] };
  t.mock.method(globalThis, "fetch", async () => Response.json(body));
  assert.deepEqual(await getMyLoans(), body);
  body = { activos: [devuelto], historial: [] };
  await assert.rejects(getMyLoans(), /formato esperado/);
});

test("conserva los estados 401 y 403", async (t) => {
  let status = 401;
  t.mock.method(globalThis, "fetch", async () => new Response("", { status }));
  await assert.rejects(getMyLoans(), (error) => error instanceof LoanError && error.status === 401);
  status = 403;
  await assert.rejects(getMyLoans(), (error) => error instanceof LoanError && error.status === 403);
});

test("informa errores de conexión", async (t) => {
  t.mock.method(globalThis, "fetch", async () => { throw new TypeError("network"); });
  await assert.rejects(getMyLoans(), /conectar con el servidor/);
});

test("propaga la cancelación del llamador", async (t) => {
  t.mock.method(globalThis, "fetch", (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener("abort", () => reject(options.signal.reason), { once: true });
  }));
  const controller = new AbortController();
  const request = getMyLoans({ signal: controller.signal });
  controller.abort();
  await assert.rejects(request, (error) => error.name === "AbortError");
});
