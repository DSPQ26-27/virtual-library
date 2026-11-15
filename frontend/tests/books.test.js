import test from "node:test";
import assert from "node:assert/strict";
import { getBooks, getCategories } from "../src/services/books.js";

const book = { id: 1, titulo: "Libro de prueba", autor: "Autor de prueba", disponible: true };
const page = { content: [book], page: 0, size: 20, totalElements: 1, totalPages: 1 };

test("devuelve la página completa y conserva las opciones HTTP de sesión", async (t) => {
  const payload = { ...page, content: [book, { ...book, id: 2, disponible: false }], totalElements: 2 };
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/libros");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.Accept, "application/json");
    assert.equal(options.cache, "no-store");
    assert.equal(options.redirect, "error");
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json(payload);
  });
  assert.deepEqual(await getBooks(), payload);
});

test("combina filtros, página cero, tamaño y orden en la URL", async (t) => {
  t.mock.method(globalThis, "fetch", async (url) => {
    assert.equal(url, "/api/libros?q=python&categoria=Inform%C3%A1tica&vista=BIBLIOTECA&page=0&size=20&sort=titulo%2Casc");
    return Response.json(page);
  });
  await getBooks({ q: "python", categoria: "Informática", vista: "BIBLIOTECA", page: 0, size: 20, sort: "titulo,asc" });
});

test("codifica espacios, tildes y caracteres especiales sin crear parámetros extra", async (t) => {
  const q = "García & C++ / 100%_? #=sí";
  t.mock.method(globalThis, "fetch", async (url) => {
    const parsed = new URL(url, "http://localhost");
    assert.equal(parsed.searchParams.get("q"), q);
    assert.equal(parsed.searchParams.get("categoria"), "Ciencia y tecnología");
    assert.equal(parsed.searchParams.size, 2);
    assert.equal(parsed.hash, "");
    assert.ok(url.includes("%26"));
    assert.ok(url.includes("%2B%2B"));
    assert.ok(url.includes("%25"));
    return Response.json(page);
  });
  await getBooks({ q, categoria: "Ciencia y tecnología" });
});

test("omite parámetros vacíos y recorta espacios exteriores", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json(page));
  await getBooks({ q: "   ", categoria: "", vista: null, page: undefined, size: "", sort: "  " });
  assert.equal(stub.mock.calls[0].arguments[0], "/api/libros");
  await getBooks({ q: "  Java  ", categoria: " Historia " });
  assert.equal(stub.mock.calls[1].arguments[0], "/api/libros?q=Java&categoria=Historia");
});

test("envía una página posterior y conserva sus metadatos", async (t) => {
  const payload = { ...page, page: 2, size: 1, totalElements: 7, totalPages: 7 };
  t.mock.method(globalThis, "fetch", async (url) => {
    const params = new URL(url, "http://localhost").searchParams;
    assert.equal(params.get("page"), "2");
    assert.equal(params.get("size"), "1");
    assert.equal(params.get("sort"), "autor,desc");
    return Response.json(payload);
  });
  assert.deepEqual(await getBooks({ page: 2, size: 1, sort: "autor,desc" }), payload);
});

for (const vista of ["TODOS", "BIBLIOTECA", "DISPONIBLES"]) {
  test(`envía la vista ${vista}`, async (t) => {
    t.mock.method(globalThis, "fetch", async (url) => {
      assert.equal(new URL(url, "http://localhost").searchParams.get("vista"), vista);
      return Response.json(page);
    });
    await getBooks({ vista });
  });
}

test("acepta cero resultados y páginas fuera del rango", async (t) => {
  const empty = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json(empty));
  assert.deepEqual(await getBooks(), empty);
  const beyond = { ...page, content: [], page: 9 };
  stub.mock.mockImplementation(async () => Response.json(beyond));
  assert.deepEqual(await getBooks({ page: 9 }), beyond);
});

test("rechaza arrays antiguos, páginas incompletas y libros incorrectos", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json(null));
  const invalid = [
    null, [], [book], {}, { content: [] },
    { ...page, content: {} }, { ...page, content: [null] },
    { ...page, content: [{ ...book, id: 0 }] },
    { ...page, content: [{ ...book, id: 1.5 }] },
    { ...page, content: [{ ...book, titulo: " " }] },
    { ...page, content: [{ ...book, autor: null }] },
    { ...page, content: [{ ...book, disponible: "true" }] },
    { ...page, content: [book, book], totalElements: 2 },
    { ...page, page: -1 }, { ...page, page: "0" },
    { ...page, size: 0 }, { ...page, size: 1.5 },
    { ...page, totalElements: -1 }, { ...page, totalElements: "1" },
    { ...page, totalElements: Number.MAX_SAFE_INTEGER + 1 },
    { ...page, totalPages: -1 }, { ...page, totalPages: 0 },
    { ...page, totalPages: "1" },
    { ...page, content: [book, { ...book, id: 2 }], size: 1, totalElements: 2, totalPages: 2 },
    { ...page, totalElements: 0, totalPages: 0 },
  ];
  for (const payload of invalid) {
    stub.mock.mockImplementation(async () => Response.json(payload));
    await assert.rejects(getBooks(), { name: "CatalogError", message: /formato esperado/ });
  }
});

test("consulta categorías con sesión y devuelve el array", async (t) => {
  const categories = ["Ciencias", "Economía", "Informática"];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/libros/categorias");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.Accept, "application/json");
    assert.equal(options.cache, "no-store");
    assert.equal(options.redirect, "error");
    return Response.json(categories);
  });
  assert.deepEqual(await getCategories(), categories);
});

test("acepta categorías vacías y rechaza elementos incorrectos", async (t) => {
  const stub = t.mock.method(globalThis, "fetch", async () => Response.json([]));
  assert.deepEqual(await getCategories(), []);
  for (const payload of [null, {}, { content: ["Historia"] }, [null], [3], [""], ["  "]]) {
    stub.mock.mockImplementation(async () => Response.json(payload));
    await assert.rejects(getCategories(), /formato esperado/);
  }
});

for (const [name, request] of [["libros", getBooks], ["categorías", getCategories]]) {
  for (const status of [400, 401, 403, 404, 500, 204]) {
    test(`${name}: conserva el error HTTP ${status} sin devolver datos vacíos`, async (t) => {
      t.mock.method(globalThis, "fetch", async () => new Response(null, { status }));
      await assert.rejects(request(), (error) => {
        assert.equal(error.name, "CatalogError");
        assert.equal(error.status, status);
        if (status === 400) assert.match(error.message, /Parámetro de búsqueda/);
        return true;
      });
    });
  }

  test(`${name}: rechaza HTML y JSON malformado`, async (t) => {
    const stub = t.mock.method(globalThis, "fetch", async () => new Response("<html>Login</html>"));
    await assert.rejects(request(), /formato esperado/);
    stub.mock.mockImplementation(async () => new Response('{"content":'));
    await assert.rejects(request(), /formato esperado/);
  });

  test(`${name}: gestiona un fallo de conexión`, async (t) => {
    t.mock.method(globalThis, "fetch", async () => { throw new TypeError("fetch failed"); });
    await assert.rejects(request(), { name: "CatalogError", message: /No se ha podido conectar/ });
  });

  test(`${name}: timeout de diez segundos con y sin señal del llamador`, async (t) => {
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
      await assert.rejects(request(withSignal ? { signal: caller.signal } : undefined), {
        name: "CatalogError", message: /tarda demasiado/,
      });
      assert.equal(caller.signal.aborted, false);
      fetchMock.mock.restore();
      timeoutMock.mock.restore();
    }
  });

  test(`${name}: conserva AbortError para señales ya canceladas`, async (t) => {
    const controller = new AbortController();
    controller.abort();
    t.mock.method(globalThis, "fetch", async (url, options) => options.signal.throwIfAborted());
    await assert.rejects(request({ signal: controller.signal }), { name: "AbortError" });
  });

  test(`${name}: permite cancelar una petición en curso`, async (t) => {
    const controller = new AbortController();
    t.mock.method(globalThis, "fetch", (url, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener("abort", () => reject(options.signal.reason), { once: true });
    }));
    const pending = request({ signal: controller.signal });
    const assertion = assert.rejects(pending, { name: "AbortError" });
    controller.abort();
    await assertion;
  });

  test(`${name}: permite cancelar durante la lectura del cuerpo HTTP`, async (t) => {
    const controller = new AbortController();
    t.mock.method(globalThis, "fetch", async (url, options) => ({
      status: 200,
      async text() {
        controller.abort();
        options.signal.throwIfAborted();
      },
    }));
    await assert.rejects(request({ signal: controller.signal }), { name: "AbortError" });
  });
}
