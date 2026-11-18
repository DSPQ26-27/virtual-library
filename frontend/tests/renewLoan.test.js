import test from "node:test";
import assert from "node:assert/strict";
import { LoanError, renewLoan } from "../src/services/loans.js";

const renovado = { id: 12, estado: "ACTIVO", numeroRenovaciones: 1, fechaVencimiento: "2026-12-31T10:00:00" };

function mockApi(t, handler) {
  t.mock.method(globalThis, "fetch", async (url, options) => url === "/api/auth/csrf"
    ? Response.json({ headerName: "X-CSRF-TOKEN", token: "token" }) : handler(url, options));
}

test("renueva con sesión y CSRF, sin enviar cuerpo ni estudiante", async (t) => {
  mockApi(t, async (url, options) => {
    assert.equal(url, "/api/prestamos/12/renovacion");
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token");
    assert.equal(options.body, undefined);
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json(renovado);
  });
  assert.deepEqual(await renewLoan(12), renovado);
});

test("muestra el motivo del rechazo del backend", async (t) => {
  let codigo;
  mockApi(t, async () => Response.json({ message: `Motivo: ${codigo}`, codigo }, { status: 409 }));
  for (codigo of ["LIMITE_RENOVACIONES", "PRESTAMO_VENCIDO", "PRESTAMO_NO_ACTIVO", "ESTUDIANTE_BLOQUEADO", "LISTA_ESPERA_PENDIENTE"]) {
    await assert.rejects(renewLoan(12), (error) => error instanceof LoanError && error.status === 409 &&
      error.code === codigo && error.message === `Motivo: ${codigo}`);
  }
});

test("conserva errores de seguridad y préstamo inexistente", async (t) => {
  let status;
  mockApi(t, async () => new Response("", { status }));
  for (status of [401, 403, 404, 409, 500]) {
    await assert.rejects(renewLoan(12), (error) => error.status === status && error.message.length > 0);
  }
});

test("rechaza respuestas de renovación incoherentes", async (t) => {
  let body;
  mockApi(t, async () => Response.json(body));
  for (body of [null, { ...renovado, id: 99 }, { ...renovado, estado: "DEVUELTO" },
    { ...renovado, numeroRenovaciones: 0 }, { ...renovado, numeroRenovaciones: 3 },
    { ...renovado, numeroRenovaciones: "1" }, { ...renovado, fechaVencimiento: null }]) {
    await assert.rejects(renewLoan(12), /confirmar la respuesta de renovación/);
  }
});

test("un fallo de red no anuncia éxito ni reenvía la renovación", async (t) => {
  let calls = 0;
  mockApi(t, async () => { calls++; throw new TypeError("sin conexión"); });
  await assert.rejects(renewLoan(12), /Comprobaremos su estado/);
  assert.equal(calls, 1);
});

test("no envía peticiones si el préstamo no es válido", async (t) => {
  t.mock.method(globalThis, "fetch", () => assert.fail("No debe enviar peticiones"));
  for (const id of [0, -1, "12", null]) await assert.rejects(renewLoan(id), /Préstamo no válido/);
});

test("no renueva si falla la obtención de CSRF", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url) => {
    calls.push(url);
    return new Response("", { status: 403 });
  });
  await assert.rejects(renewLoan(12), /preparar la operación/);
  assert.deepEqual(calls, ["/api/auth/csrf"]);
});
