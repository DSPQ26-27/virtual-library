import { csrfToken } from "./session.js";

/** Error de la gestión del catálogo, con los mensajes por campo que envía el backend. */
export class CatalogAdminError extends Error {
  constructor(message, status = 0, fieldErrors = {}) {
    super(message);
    this.name = "CatalogAdminError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const MESSAGES = {
  400: "Revisa los datos del libro.",
  401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
  403: "No tienes permiso para gestionar el catálogo.",
  404: "El libro no existe.",
  409: "Ya existe un libro con ese ISBN.",
};

function isBookDetail(book) {
  return (
    book !== null && typeof book === "object" &&
    Number.isSafeInteger(book.id) && book.id > 0 &&
    typeof book.titulo === "string" && typeof book.autor === "string" &&
    typeof book.retirado === "boolean"
  );
}

/** Petición común: sesión, timeout de 10 s y token CSRF en las operaciones que modifican. */
async function send(url, { method = "GET", data } = {}) {
  const headers = { Accept: "application/json" };
  if (method !== "GET") headers["X-CSRF-TOKEN"] = await csrfToken();
  if (data !== undefined) headers["Content-Type"] = "application/json";

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10000),
      ...(data !== undefined ? { body: JSON.stringify(data) } : {}),
    });
  } catch (error) {
    throw new CatalogAdminError(
      error.name === "TimeoutError"
        ? "El servidor tarda demasiado en responder. Inténtalo más tarde."
        : "No se ha podido conectar con el servidor."
    );
  }

  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    // Respuesta vacía o no JSON: se trata como error genérico.
  }
  return { status: response.status, body };
}

/** Convierte una respuesta de error del backend ({message, errors}) en CatalogAdminError. */
function fail(status, body) {
  const errors = body?.errors;
  const fieldErrors =
    errors && typeof errors === "object" && !Array.isArray(errors)
      ? Object.fromEntries(Object.entries(errors).filter(([, value]) => typeof value === "string"))
      : {};
  const message =
    status === 401 || status === 403
      ? MESSAGES[status]
      : (typeof body?.message === "string" && body.message) || MESSAGES[status] ||
        "No se ha podido completar la operación. Inténtalo más tarde.";
  throw new CatalogAdminError(message, status, fieldErrors);
}

function expectBook(status, body, okStatus) {
  if (status === okStatus && isBookDetail(body)) return body;
  if (status === okStatus) {
    throw new CatalogAdminError("La respuesta del servidor no tiene el formato esperado.", status);
  }
  fail(status, body);
}

export async function getBook(id) {
  const { status, body } = await send(`/api/libros/${encodeURIComponent(id)}`);
  return expectBook(status, body, 200);
}

export async function createBook(book) {
  const { status, body } = await send("/api/libros", { method: "POST", data: book });
  return expectBook(status, body, 201);
}

export async function updateBook(id, book) {
  const { status, body } = await send(`/api/libros/${encodeURIComponent(id)}`, { method: "PUT", data: book });
  return expectBook(status, body, 200);
}

export async function retireBook(id) {
  const { status, body } = await send(`/api/libros/${encodeURIComponent(id)}`, { method: "DELETE" });
  if (status !== 204) fail(status, body);
}
