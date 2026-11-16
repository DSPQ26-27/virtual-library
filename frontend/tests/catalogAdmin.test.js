import test from "node:test";
import assert from "node:assert/strict";
import {
  CatalogAdminError, createBook, getBook, retireBook, updateBook,
} from "../src/services/catalogAdmin.js";
import { canManageCatalog } from "../src/auth/roles.js";
import { EMPTY_BOOK, toFormValues, toRequest, validateBook } from "../src/services/bookValidation.js";

// Pruebas de la gestión del catálogo en el frontend (#52).

const book = {
  id: 5, isbn: "978-84-376-0494-7", titulo: "Don Quijote de la Mancha",
  autor: "Miguel de Cervantes", editorial: "Cátedra", categoria: "Clásicos",
  anioPublicacion: 1605, descripcion: null, portadaUrl: null,
  origen: "MANUAL", retirado: false,
};

const datos = { ...book };
delete datos.id;
delete datos.origen;
delete datos.retirado;

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

// ---------- Llamadas a la API ----------

test("getBook consulta la ficha con sesión y sin pedir token CSRF", async (t) => {
  const calls = mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros/5");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.Accept, "application/json");
    assert.equal(options.headers["X-CSRF-TOKEN"], undefined);
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json(book);
  });

  assert.deepEqual(await getBook(5), book);
  assert.equal(calls.length, 1);
});

test("createBook pide el token CSRF y envía un POST con el JSON", async (t) => {
  const calls = mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token-prueba");
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(options.body), datos);
    return Response.json(book, { status: 201 });
  });

  assert.deepEqual(await createBook(datos), book);
  assert.deepEqual(calls.map((call) => call.url), ["/api/auth/csrf", "/api/libros"]);
});

test("updateBook envía un PUT con token y acepta 200", async (t) => {
  mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros/5");
    assert.equal(options.method, "PUT");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token-prueba");
    return Response.json({ ...book, titulo: "Editado" });
  });

  assert.equal((await updateBook(5, { ...datos, titulo: "Editado" })).titulo, "Editado");
});

test("retireBook envía un DELETE con token y acepta 204 sin cuerpo", async (t) => {
  mockApi(t, async (url, options) => {
    assert.equal(url, "/api/libros/5");
    assert.equal(options.method, "DELETE");
    assert.equal(options.headers["X-CSRF-TOKEN"], "token-prueba");
    assert.equal(options.body, undefined);
    return new Response(null, { status: 204 });
  });

  assert.equal(await retireBook(5), undefined);
});

test("codifica el id en la URL", async (t) => {
  mockApi(t, async (url) => {
    assert.equal(url, "/api/libros/5%2F..%2Fx");
    return Response.json(book);
  });

  await getBook("5/../x");
});

// ---------- Errores ----------

test("un 400 conserva el mensaje y los errores por campo del backend", async (t) => {
  mockApi(t, async () => Response.json(
    { message: "Revisa los datos del libro.", errors: { titulo: "El título es obligatorio.", raro: 3 } },
    { status: 400 }
  ));

  const error = await rejects(createBook({ ...datos, titulo: "" }));
  assert.ok(error instanceof CatalogAdminError);
  assert.equal(error.status, 400);
  assert.equal(error.message, "Revisa los datos del libro.");
  assert.deepEqual(error.fieldErrors, { titulo: "El título es obligatorio." });
});

test("un 409 indica el ISBN duplicado en su campo", async (t) => {
  mockApi(t, async () => Response.json(
    { message: "Ya existe un libro con ese ISBN.", errors: { isbn: "Este ISBN ya está registrado en otro libro." } },
    { status: 409 }
  ));

  const error = await rejects(createBook(datos));
  assert.equal(error.status, 409);
  assert.equal(error.fieldErrors.isbn, "Este ISBN ya está registrado en otro libro.");
});

test("un 403 muestra que no hay permiso, aunque el backend envíe otro texto", async (t) => {
  mockApi(t, async () => Response.json({ message: "Acceso denegado." }, { status: 403 }));

  const error = await rejects(retireBook(5));
  assert.equal(error.status, 403);
  assert.equal(error.message, "No tienes permiso para gestionar el catálogo.");
});

test("un 404 indica que el libro no existe", async (t) => {
  mockApi(t, async () => Response.json({ message: "El libro no existe.", errors: {} }, { status: 404 }));

  const error = await rejects(getBook(999));
  assert.equal(error.status, 404);
  assert.equal(error.message, "El libro no existe.");
  assert.deepEqual(error.fieldErrors, {});
});

test("un 401 conserva el estado para expirar la sesión", async (t) => {
  mockApi(t, async () => new Response("", { status: 401 }));

  const error = await rejects(getBook(5));
  assert.equal(error.status, 401);
  assert.equal(error.message, "Tu sesión ha caducado. Inicia sesión de nuevo.");
});

test("rechaza un 200 con formato incorrecto", async (t) => {
  mockApi(t, async () => Response.json({ id: 5, titulo: "Sin autor" }));

  const error = await rejects(getBook(5));
  assert.equal(error.message, "La respuesta del servidor no tiene el formato esperado.");
});

test("gestiona un fallo de conexión", async (t) => {
  mockApi(t, async () => {
    throw new TypeError("fetch failed");
  });

  const error = await rejects(getBook(5));
  assert.ok(error instanceof CatalogAdminError);
  assert.equal(error.message, "No se ha podido conectar con el servidor.");
});

// ---------- Roles ----------

test("solo bibliotecarios y administradores pueden gestionar el catálogo", () => {
  assert.equal(canManageCatalog({ roles: ["STUDENT"] }), false);
  assert.equal(canManageCatalog({ roles: ["LIBRARIAN"] }), true);
  assert.equal(canManageCatalog({ roles: ["ADMIN"] }), true);
  assert.equal(canManageCatalog(null), false);
  assert.equal(canManageCatalog({}), false);
});

// ---------- Validación del formulario ----------

test("un libro válido no tiene errores", () => {
  assert.deepEqual(validateBook(toFormValues(book)), {});
  assert.deepEqual(validateBook({ ...EMPTY_BOOK, titulo: "Título", autor: "Autor" }), {});
});

test("valida con los mismos mensajes que el backend", () => {
  const errors = validateBook({
    ...EMPTY_BOOK,
    titulo: "   ",
    isbn: "123",
    anioPublicacion: "3000",
    portadaUrl: "ftp://covers.example.com/a.jpg",
  });

  assert.deepEqual(errors, {
    titulo: "El título es obligatorio.",
    autor: "El autor es obligatorio.",
    isbn: "ISBN no válido: debe tener 10 o 13 dígitos.",
    anioPublicacion: "Año de publicación no válido.",
    portadaUrl: "La portada debe ser una URL http o https.",
  });
});

test("acepta ISBN de 10 y 13 dígitos con o sin guiones", () => {
  for (const isbn of ["9788437604947", "978-84-376-0494-7", "0-306-40615-2", "080442957X"]) {
    assert.equal(validateBook({ ...EMPTY_BOOK, titulo: "T", autor: "A", isbn }).isbn, undefined, isbn);
  }
});

test("controla las longitudes máximas", () => {
  const errors = validateBook({ ...EMPTY_BOOK, titulo: "x".repeat(256), autor: "A", categoria: "c".repeat(101) });
  assert.equal(errors.titulo, "No puede superar 255 caracteres.");
  assert.equal(errors.categoria, "No puede superar 100 caracteres.");
});

test("toRequest recorta, convierte vacíos en null y el año en número", () => {
  assert.deepEqual(toRequest({
    ...EMPTY_BOOK, titulo: "  Título  ", autor: " Autor ", editorial: "   ", anioPublicacion: "1605",
  }), {
    isbn: null, titulo: "Título", autor: "Autor", editorial: null, categoria: null,
    anioPublicacion: 1605, descripcion: null, portadaUrl: null,
  });
});

test("toFormValues convierte null en texto vacío", () => {
  const values = toFormValues(book);
  assert.equal(values.descripcion, "");
  assert.equal(values.anioPublicacion, "1605");
  assert.deepEqual(Object.keys(values), Object.keys(EMPTY_BOOK));
});
