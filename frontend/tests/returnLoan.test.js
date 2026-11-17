import test from "node:test";
import assert from "node:assert/strict";
import { returnLoan } from "../src/services/loans.js";

test("envía la devolución con sesión y token CSRF", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push(url);
    if (url === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "token" });
    assert.equal(url, "/api/prestamos/12/devolucion");
    assert.equal(options.method, "POST"); assert.equal(options.credentials, "include");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token");
    assert.equal(options.body, undefined);
    return Response.json({ id: 12, estado: "DEVUELTO", fechaDevolucion: "2026-11-17T12:00:00" });
  });
  assert.equal((await returnLoan(12)).estado, "DEVUELTO");
  assert.deepEqual(calls, ["/api/auth/csrf", "/api/prestamos/12/devolucion"]);
});

test("conserva errores HTTP de seguridad y negocio", async (t) => {
  let status = 401;
  t.mock.method(globalThis, "fetch", async (url) => url === "/api/auth/csrf"
    ? Response.json({ headerName: "X-CSRF-TOKEN", token: "token" })
    : Response.json({ codigo: "ERROR" }, { status }));
  for (status of [401, 403, 404, 409]) {
    await assert.rejects(returnLoan(1), (error) => error.status === status && error.message.length > 0);
  }
});

test("trata fallos de red y respuestas de éxito inválidas como resultado incierto", async (t) => {
  let fallo = true;
  t.mock.method(globalThis, "fetch", async (url) => {
    if (url === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "token" });
    if (fallo) throw new TypeError("sin conexión");
    return Response.json({ id: 1, estado: "ACTIVO" });
  });
  await assert.rejects(returnLoan(1), /confirmar la devolución/);
  fallo = false;
  await assert.rejects(returnLoan(1), /confirmar la respuesta/);
});

test("no envía la devolución si falla la obtención del token", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url) => { calls.push(url); return new Response("", { status: 403 }); });
  await assert.rejects(returnLoan(1));
  assert.deepEqual(calls, ["/api/auth/csrf"]);
});
