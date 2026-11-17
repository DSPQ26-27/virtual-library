import test from "node:test";
import assert from "node:assert/strict";
import { formatDueDate, LoanError, requestLoan } from "../src/services/loans.js";
import { isStudent } from "../src/auth/roles.js";

// Pruebas de la solicitud de préstamos en el frontend (#59).

const loan = {
  id: 12, libroId: 5, titulo: "Don Quijote de la Mancha", ejemplarCodigo: "EJ-0001",
  fechaPrestamo: "2026-11-17T12:40:00", fechaVencimiento: "2026-12-17T12:40:00", estado: "ACTIVO",
};

/** Simula fetch: responde al token CSRF y delega el resto en la función recibida. */
function mockApi(t, handler) {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    if (url === "/api/auth/csrf") {
      return Response.json({ headerName: "X-CSRF-TOKEN", token: "token-prueba" });
    }
    return handler(url, options);
  });
  return calls;
}

async function rejects(promise) {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  assert.fail("Se esperaba un error");
}

// ---------- Llamada a la API ----------

test("requestLoan pide el token CSRF y envía un POST con el libro", async (t) => {
  const calls = mockApi(t, async (url, options) => {
    assert.equal(url, "/api/prestamos");
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token-prueba");
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(options.body), { libroId: 5 });
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json(loan, { status: 201 });
  });

  assert.deepEqual(await requestLoan(5), loan);
  assert.deepEqual(calls.map((call) => call.url), ["/api/auth/csrf", "/api/prestamos"]);
});

// ---------- Errores ----------

test("un 409 conserva el mensaje y el código del backend", async (t) => {
  const casos = [
    ["LIMITE_PRESTAMOS", "Has alcanzado el límite de 5 préstamos activos."],
    ["PRESTAMOS_VENCIDOS", "Tienes préstamos vencidos sin devolver. Devuélvelos para poder pedir otro libro."],
    ["SIN_EJEMPLARES", "No quedan ejemplares disponibles de este libro."],
  ];

  for (const [codigo, message] of casos) {
    t.mock.restoreAll();
    mockApi(t, async () => Response.json({ message, codigo, errors: {} }, { status: 409 }));

    const error = await rejects(requestLoan(5));
    assert.ok(error instanceof LoanError);
    assert.equal(error.status, 409);
    assert.equal(error.code, codigo);
    assert.equal(error.message, message);
  }
});

test("un 401 conserva el estado para expirar la sesión", async (t) => {
  mockApi(t, async () => new Response("", { status: 401 }));

  const error = await rejects(requestLoan(5));
  assert.equal(error.status, 401);
  assert.equal(error.message, "Tu sesión ha caducado. Inicia sesión de nuevo.");
});

test("un 403 muestra que solo los estudiantes pueden pedir préstamos", async (t) => {
  mockApi(t, async () => Response.json({ message: "Forbidden" }, { status: 403 }));

  const error = await rejects(requestLoan(5));
  assert.equal(error.status, 403);
  assert.equal(error.message, "Solo los estudiantes pueden pedir préstamos.");
});

test("un 404 indica que el libro no existe", async (t) => {
  mockApi(t, async () => Response.json(
    { message: "El libro no existe.", codigo: "LIBRO_NO_ENCONTRADO", errors: {} }, { status: 404 }));

  const error = await rejects(requestLoan(999));
  assert.equal(error.status, 404);
  assert.equal(error.code, "LIBRO_NO_ENCONTRADO");
  assert.equal(error.message, "El libro no existe.");
});

test("rechaza un 201 con formato incorrecto", async (t) => {
  const { ejemplarCodigo: _sinCodigo, ...incompleto } = loan;
  mockApi(t, async () => Response.json(incompleto, { status: 201 }));

  const error = await rejects(requestLoan(5));
  assert.equal(error.message, "La respuesta del servidor no tiene el formato esperado.");
});

test("gestiona un fallo de conexión", async (t) => {
  mockApi(t, async () => {
    throw new TypeError("fetch failed");
  });

  const error = await rejects(requestLoan(5));
  assert.ok(error instanceof LoanError);
  assert.equal(error.message, "No se ha podido conectar con el servidor.");
});

// ---------- Funciones auxiliares ----------

test("solo los estudiantes pueden pedir préstamos", () => {
  assert.equal(isStudent({ roles: ["STUDENT"] }), true);
  assert.equal(isStudent({ roles: ["LIBRARIAN"] }), false);
  assert.equal(isStudent({ roles: ["ADMIN"] }), false);
  assert.equal(isStudent(null), false);
  assert.equal(isStudent({}), false);
});

test("formatDueDate muestra la fecha en formato español", () => {
  assert.equal(formatDueDate("2026-12-17T12:40:00"), "17/12/2026");
  assert.equal(formatDueDate("2026-01-05T23:59:59.123456"), "05/01/2026");
  assert.equal(formatDueDate("no es una fecha"), "");
  assert.equal(formatDueDate(null), "");
});
