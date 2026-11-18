import test from "node:test";
import assert from "node:assert/strict";
import { joinWaitlist, getMyWaitlist, cancelWaitlist, WaitlistError } from "../src/services/waitlist.js";

const solicitud = { id: 7, libroId: 25, titulo: "Libro", fechaSolicitud: "2026-11-18T12:00:00", estado: "PENDIENTE" };

function mockApi(t, handler) {
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "prueba" });
    return handler(url, options);
  });
}

test("apuntarse envía libroId con sesión y CSRF", async (t) => {
  mockApi(t, (url, options) => {
    assert.equal(url, "/api/espera");
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers["X-CSRF-TOKEN"], "prueba");
    assert.deepEqual(JSON.parse(options.body), { libroId: 25 });
    return Response.json(solicitud, { status: 201 });
  });
  assert.deepEqual(await joinWaitlist(25), solicitud);
});

test("consulta solicitudes propias y rechaza respuestas inválidas", async (t) => {
  mockApi(t, (url) => {
    assert.equal(url, "/api/espera/mias");
    return Response.json([solicitud]);
  });
  assert.deepEqual(await getMyWaitlist(), [solicitud]);
  t.mock.restoreAll();
  mockApi(t, () => Response.json([{ id: 7 }]));
  await assert.rejects(getMyWaitlist(), WaitlistError);
});

test("cancelación envía CSRF y acepta CANCELADA", async (t) => {
  mockApi(t, (url, options) => {
    assert.equal(url, "/api/espera/7/cancelacion");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["X-CSRF-TOKEN"], "prueba");
    return Response.json({ ...solicitud, estado: "CANCELADA" });
  });
  assert.equal((await cancelWaitlist(7)).estado, "CANCELADA");
});

test("conserva errores HTTP de sesión, permisos y negocio", async (t) => {
  for (const status of [401, 403, 404, 409]) {
    t.mock.restoreAll();
    mockApi(t, () => Response.json({ message: "Solicitud rechazada" }, { status }));
    await assert.rejects(joinWaitlist(25), (error) =>
      error.status === status && error.message === "Solicitud rechazada");
  }
});

test("errores de red y JSON inválido permiten comprobar después el estado", async (t) => {
  mockApi(t, () => { throw new Error("Sin red"); });
  await assert.rejects(joinWaitlist(25), /Mis reservas/);
  t.mock.restoreAll();
  mockApi(t, () => new Response("<html>Error</html>"));
  await assert.rejects(getMyWaitlist(), WaitlistError);
});
