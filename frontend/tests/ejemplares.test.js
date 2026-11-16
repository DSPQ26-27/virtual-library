import test from "node:test";
import assert from "node:assert/strict";
import {
  EjemplaresError, getEjemplares, createEjemplar, updateEjemplar, retireEjemplar, validateEjemplar,
} from "../src/services/ejemplares.js";

const ejemplar = { id: 7, codigo: "EJ-0007", estado: "DISPONIBLE", activo: true };
const datos = { codigo: "EJ-0007", estado: "DISPONIBLE" };
const listado = { ejemplares: [ejemplar], total: 1, disponibles: 1 };

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

test("consulta los ejemplares y contadores con sesión, sin pedir CSRF", async (t) => {
  const payload = {
    ejemplares: [ejemplar, { ...ejemplar, id: 8, codigo: "EJ-0008", activo: false }],
    total: 2, disponibles: 1,
  };
  const calls = mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros/5/ejemplares");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");
    assert.equal(options.cache, "no-store");
    assert.equal(options.redirect, "error");
    assert.equal(options.headers.Accept, "application/json");
    assert.equal(options.headers["X-CSRF-TOKEN"], undefined);
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json(payload);
  });
  assert.deepEqual(await getEjemplares(5), payload);
  assert.equal(calls.length, 1);
});

test("acepta un libro sin ejemplares", async (t) => {
  const empty = { ejemplares: [], total: 0, disponibles: 0 };
  mockApi(t, async () => Response.json(empty));
  assert.deepEqual(await getEjemplares(5), empty);
});

for (const [name, method, request, status, response] of [
  ["alta", "POST", () => createEjemplar(5, datos), 201, ejemplar],
  ["edición", "PUT", () => updateEjemplar(5, 7, datos), 200, ejemplar],
  ["retirada", "DELETE", () => retireEjemplar(5, 7), 204, undefined],
]) {
  test(`${name}: usa la ruta, método y token CSRF correctos`, async (t) => {
    const expectedUrl = method === "POST" ? "/api/libros/5/ejemplares" : "/api/libros/5/ejemplares/7";
    const calls = mockApi(t, async (url, options) => {
      assert.equal(url, expectedUrl);
      assert.equal(options.method, method);
      assert.equal(options.credentials, "include");
      assert.equal(options.headers["X-CSRF-TOKEN"], "token-prueba");
      if (method === "DELETE") {
        assert.equal(options.body, undefined);
        return new Response(null, { status });
      }
      assert.equal(options.headers["Content-Type"], "application/json");
      assert.deepEqual(JSON.parse(options.body), datos);
      return Response.json(response, { status });
    });
    assert.deepEqual(await request(), response);
    assert.deepEqual(calls.map((call) => call.url), ["/api/auth/csrf", expectedUrl]);
  });
}

test("codifica los dos identificadores sin permitir segmentos adicionales", async (t) => {
  mockApi(t, async (url) => {
    assert.equal(url, "/api/libros/5%2Fx/ejemplares/7%3Fy");
    return Response.json(ejemplar);
  });
  await updateEjemplar("5/x", "7?y", datos);
});

test("conserva los errores por campo del backend y descarta valores inválidos", async (t) => {
  mockApi(t, async () => Response.json({
    message: "Revisa los datos del ejemplar.", errors: { codigo: "El código es obligatorio.", otro: 4 },
  }, { status: 400 }));
  await assert.rejects(createEjemplar(5, datos), (error) => {
    assert.ok(error instanceof EjemplaresError);
    assert.equal(error.status, 400);
    assert.equal(error.message, "Revisa los datos del ejemplar.");
    assert.deepEqual(error.fieldErrors, { codigo: "El código es obligatorio." });
    return true;
  });
});

test("un código duplicado muestra el mensaje de conflicto y el error de campo", async (t) => {
  mockApi(t, async () => Response.json({
    message: "Ya existe un ejemplar con ese código.",
    errors: { codigo: "Este código ya está registrado en otro ejemplar." },
  }, { status: 409 }));
  await assert.rejects(updateEjemplar(5, 7, datos), (error) => {
    assert.equal(error.status, 409);
    assert.equal(error.message, "Ya existe un ejemplar con ese código.");
    assert.equal(error.fieldErrors.codigo, "Este código ya está registrado en otro ejemplar.");
    return true;
  });
});

test("retirar un ejemplar con préstamo activo rechaza 409 y conserva el motivo", async (t) => {
  const message = "No se puede retirar un ejemplar con un préstamo activo.";
  const calls = mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros/5/ejemplares/7");
    assert.equal(options.method, "DELETE");
    return Response.json({ message, errors: {} }, { status: 409 });
  });

  await assert.rejects(retireEjemplar(5, 7), (error) => {
    assert.ok(error instanceof EjemplaresError);
    assert.equal(error.status, 409);
    assert.equal(error.message, message);
    assert.deepEqual(error.fieldErrors, {});
    return true;
  });
  assert.equal(calls.filter((call) => call.options.method === "DELETE").length, 1);
});

test("editar el estado con préstamo activo conserva el conflicto del backend", async (t) => {
  const message = "No se puede cambiar el estado de un ejemplar con un préstamo activo.";
  mockApi(t, async () => Response.json({ message, errors: {} }, { status: 409 }));

  await assert.rejects(updateEjemplar(5, 7, datos), (error) => {
    assert.equal(error.status, 409);
    assert.equal(error.message, message);
    assert.deepEqual(error.fieldErrors, {});
    return true;
  });
});

test("retirar un ejemplar inexistente rechaza 404 conservando el mensaje", async (t) => {
  const message = "El ejemplar no existe.";
  mockApi(t, async () => Response.json({ message, errors: {} }, { status: 404 }));
  await assert.rejects(retireEjemplar(5, 7), { name: "EjemplaresError", status: 404, message });
});

for (const status of [400, 401, 403, 404, 409, 500]) {
  test(`conserva el estado HTTP ${status} aunque no reciba JSON`, async (t) => {
    mockApi(t, async () => new Response("Error", { status }));
    await assert.rejects(getEjemplares(5), (error) => {
      assert.ok(error instanceof EjemplaresError);
      assert.equal(error.status, status);
      assert.ok(error.message);
      assert.deepEqual(error.fieldErrors, {});
      return true;
    });
  });
}

test("401 y 403 usan mensajes de sesión y permisos aunque el servidor envíe otro texto", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json({ message: "Otro texto" }, { status: 401 }));
  await assert.rejects(getEjemplares(5), { status: 401, message: /sesión ha caducado/ });
  stub.mock.mockImplementation(async () => Response.json({ message: "Otro texto" }, { status: 403 }));
  await assert.rejects(getEjemplares(5), { status: 403, message: /No tienes permiso/ });
});

test("rechaza listados incompletos, estados inválidos y contadores incoherentes", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json(null));
  for (const payload of [
    null, [], {}, { ejemplares: [] },
    { ...listado, total: 2 }, { ...listado, disponibles: 0 },
    { ...listado, disponibles: -1 }, { ...listado, total: "1" },
    { ...listado, ejemplares: [null] },
    { ...listado, ejemplares: [{ ...ejemplar, id: 0 }] },
    { ...listado, ejemplares: [{ ...ejemplar, codigo: " " }] },
    { ...listado, ejemplares: [{ ...ejemplar, estado: "ROTO" }] },
    { ...listado, ejemplares: [{ ...ejemplar, activo: "true" }] },
    { ejemplares: [ejemplar, ejemplar], total: 2, disponibles: 2 },
  ]) {
    stub.mock.mockImplementation(async () => Response.json(payload));
    await assert.rejects(getEjemplares(5), { name: "EjemplaresError", message: /formato esperado/ });
  }
});

test("alta y edición rechazan respuestas exitosas sin un ejemplar válido", async (t) => {
  mockApi(t, async (url, options) => Response.json({ ...ejemplar, estado: "ROTO" }, {
    status: options.method === "POST" ? 201 : 200,
  }));
  await assert.rejects(createEjemplar(5, datos), /formato esperado/);
  await assert.rejects(updateEjemplar(5, 7, datos), /formato esperado/);
});

test("retirar requiere 204 y conserva un conflicto al editar un ejemplar retirado", async (t) => {
  mockApi(t, async () => Response.json({ message: "No se puede editar un ejemplar retirado." }, { status: 409 }));
  await assert.rejects(updateEjemplar(5, 7, datos), { status: 409, message: /ejemplar retirado/ });
  await assert.rejects(retireEjemplar(5, 7), { status: 409 });
});

test("rechaza HTML y JSON mal formado con HTTP 200", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => new Response("<html>Login</html>"));
  await assert.rejects(getEjemplares(5), /formato esperado/);
  stub.mock.mockImplementation(async () => new Response('{"ejemplares":'));
  await assert.rejects(getEjemplares(5), /formato esperado/);
});

test("un fallo al pedir CSRF impide enviar la modificación", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 500 }));
  await assert.rejects(createEjemplar(5, datos), { name: "EjemplaresError", message: /preparar la operación/ });
  assert.equal(stub.mock.callCount(), 1);
  assert.equal(stub.mock.calls[0].arguments[0], "/api/auth/csrf");
});

test("gestiona fallos de red y fallos durante la lectura del cuerpo", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => { throw new TypeError("fetch failed"); });
  await assert.rejects(getEjemplares(5), { name: "EjemplaresError", message: /No se ha podido conectar/ });
  stub.mock.mockImplementation(async () => ({ status: 200, async text() { throw new TypeError("connection closed"); } }));
  await assert.rejects(getEjemplares(5), { name: "EjemplaresError", message: /No se ha podido conectar/ });
});

test("limita a diez segundos la petición con y sin señal externa", async (t) => {
  for (const withSignal of [false, true]) {
    const timeout = new AbortController();
    const timeoutMock = t.mock.method(AbortSignal, "timeout", (milliseconds) => {
      assert.equal(milliseconds, 10000);
      return timeout.signal;
    });
    const fetchMock = t.mock.method(globalThis, "fetch", async (url, options) => {
      timeout.abort(new DOMException("Timeout", "TimeoutError"));
      options.signal.throwIfAborted();
    });
    const caller = new AbortController();
    await assert.rejects(getEjemplares(5, withSignal ? { signal: caller.signal } : undefined), {
      name: "EjemplaresError", message: /tarda demasiado/,
    });
    assert.equal(caller.signal.aborted, false);
    fetchMock.mock.restore();
    timeoutMock.mock.restore();
  }
});

test("permite cancelar una consulta en curso sin convertirla en error de conexión", async (t) => {
  const caller = new AbortController();
  t.mock.method(globalThis, "fetch", (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener("abort", () => reject(options.signal.reason), { once: true });
  }));
  const pending = getEjemplares(5, { signal: caller.signal });
  const assertion = assert.rejects(pending, { name: "AbortError" });
  caller.abort();
  await assertion;
});

test("valida código obligatorio, longitud recortada y los dos estados existentes", () => {
  assert.deepEqual(validateEjemplar({ codigo: "   ", estado: "ROTO" }), {
    codigo: "El código es obligatorio.", estado: "Selecciona un estado válido.",
  });
  assert.deepEqual(validateEjemplar({ codigo: "x".repeat(51), estado: "DISPONIBLE" }), {
    codigo: "El código no puede superar 50 caracteres.",
  });
  assert.deepEqual(validateEjemplar({ codigo: `  ${"x".repeat(50)}  `, estado: "DISPONIBLE" }), {});
  assert.deepEqual(validateEjemplar({ codigo: "EJ-1", estado: "PRESTADO" }), {});
  assert.deepEqual(Object.keys(validateEjemplar()), ["codigo", "estado"]);
});
