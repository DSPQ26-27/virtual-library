import test from "node:test";
import assert from "node:assert/strict";
import { getBooks } from "../src/services/books.js";

const book = {
  id: 1,
  titulo: "Libro de prueba",
  autor: "Autor de prueba",
  disponible: true,
};

test("solicita los libros con la sesión y conserva su disponibilidad", async (t) => {
  const books = [
    book,
    { ...book, id: 2, disponible: false },
  ];

  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/libros");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");

    return Response.json(books);
  });

  assert.deepEqual(await getBooks(), books);
});

test("acepta un catálogo vacío", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => Response.json([])
  );

  assert.deepEqual(await getBooks(), []);
});

for (const status of [401, 403]) {
  test(`conserva el estado HTTP ${status}`, async (t) => {
    t.mock.method(
      globalThis,
      "fetch",
      async () => new Response(null, { status })
    );

    await assert.rejects(getBooks(), {
      name: "CatalogError",
      status,
    });
  });
}

test("rechaza formatos incorrectos, libros incompletos e IDs duplicados", async (t) => {
  const stub = t.mock.method(
    globalThis,
    "fetch",
    async () => Response.json({})
  );

  const invalidResponses = [
    { content: [] },
    [{ id: 1 }],
    [book, book],
  ];

  for (const payload of invalidResponses) {
    stub.mock.mockImplementation(
      async () => Response.json(payload)
    );

    await assert.rejects(
      getBooks(),
      /formato esperado/
    );
  }
});

test("una respuesta HTML no se presenta como catálogo vacío", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response("<html>Login</html>")
  );

  await assert.rejects(
    getBooks(),
    /formato esperado/
  );
});

test("gestiona un fallo de conexión", async (t) => {
  t.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("fetch failed");
  });

  await assert.rejects(
    getBooks(),
    /No se ha podido conectar/
  );
});

test("gestiona el tiempo de espera agotado", async (t) => {
  t.mock.method(globalThis, "fetch", async () => {
    throw new DOMException("Timeout", "TimeoutError");
  });

  await assert.rejects(
    getBooks(),
    /tarda demasiado/
  );
});

test("permite cancelar una petición al abandonar la pantalla", async (t) => {
  const controller = new AbortController();
  controller.abort();

  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(options.signal.aborted, true);
    throw new DOMException("Cancelada", "AbortError");
  });

  await assert.rejects(
    getBooks({ signal: controller.signal }),
    { name: "AbortError" }
  );
});