import { csrfToken } from "./session.js";

export class EjemplaresError extends Error {
  constructor(message, status = 0, fieldErrors = {}) {
    super(message);
    this.name = "EjemplaresError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const ESTADOS = ["DISPONIBLE", "PRESTADO"];
const MESSAGES = {
  400: "Revisa los datos del ejemplar.",
  401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
  403: "No tienes permiso para gestionar los ejemplares.",
  404: "El libro o el ejemplar no existe.",
  409: "No se ha podido guardar el ejemplar. Revisa sus datos.",
};

export function validateEjemplar({ codigo, estado } = {}) {
  const errors = {};
  const code = typeof codigo === "string" ? codigo.trim() : "";
  if (!code) errors.codigo = "El código es obligatorio.";
  else if (code.length > 50) errors.codigo = "El código no puede superar 50 caracteres.";
  if (!ESTADOS.includes(estado)) errors.estado = "Selecciona un estado válido.";
  return errors;
}

function isEjemplar(value) {
  return value !== null && typeof value === "object" &&
    Number.isSafeInteger(value.id) && value.id > 0 &&
    typeof value.codigo === "string" && value.codigo.trim().length > 0 &&
    value.codigo.length <= 50 && ESTADOS.includes(value.estado) &&
    typeof value.activo === "boolean";
}

function isListado(value) {
  return value !== null && typeof value === "object" &&
    Array.isArray(value.ejemplares) && value.ejemplares.every(isEjemplar) &&
    new Set(value.ejemplares.map((item) => item.id)).size === value.ejemplares.length &&
    Number.isSafeInteger(value.total) && value.total === value.ejemplares.length &&
    Number.isSafeInteger(value.disponibles) &&
    value.disponibles === value.ejemplares.filter((item) => item.activo && item.estado === "DISPONIBLE").length;
}

async function send(url, { method = "GET", data, signal } = {}) {
  const headers = { Accept: "application/json" };
  if (method !== "GET") {
    try {
      headers["X-CSRF-TOKEN"] = await csrfToken();
    } catch (error) {
      throw new EjemplaresError(error.message);
    }
  }
  if (data !== undefined) headers["Content-Type"] = "application/json";
  const timeout = AbortSignal.timeout(10000);

  let response;
  let text;
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      redirect: "error",
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      ...(data !== undefined ? { body: JSON.stringify(data) } : {}),
    });
    text = await response.text();
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new EjemplaresError(
      error.name === "TimeoutError" || error.name === "AbortError"
        ? "El servidor tarda demasiado en responder. Inténtalo más tarde."
        : "No se ha podido conectar con el servidor."
    );
  }

  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    // HTML o JSON mal formado no cumplen el contrato de la API.
  }
  return { status: response.status, body };
}

function fail(status, body) {
  const errors = body?.errors;
  const fieldErrors = errors && typeof errors === "object" && !Array.isArray(errors)
    ? Object.fromEntries(Object.entries(errors).filter(([, value]) => typeof value === "string"))
    : {};
  const message = status === 401 || status === 403
    ? MESSAGES[status]
    : (typeof body?.message === "string" && body.message) || MESSAGES[status] ||
      "No se ha podido completar la operación. Inténtalo más tarde.";
  throw new EjemplaresError(message, status, fieldErrors);
}

function expect({ status, body }, expectedStatus, isValid) {
  if (status !== expectedStatus) fail(status, body);
  if (!isValid(body)) {
    throw new EjemplaresError("La respuesta del servidor no tiene el formato esperado.", status);
  }
  return body;
}

function baseUrl(libroId) {
  return `/api/libros/${encodeURIComponent(libroId)}/ejemplares`;
}

export async function getEjemplares(libroId, { signal } = {}) {
  return expect(await send(baseUrl(libroId), { signal }), 200, isListado);
}

export async function createEjemplar(libroId, datos) {
  return expect(await send(baseUrl(libroId), { method: "POST", data: datos }), 201, isEjemplar);
}

export async function updateEjemplar(libroId, ejemplarId, datos) {
  return expect(await send(`${baseUrl(libroId)}/${encodeURIComponent(ejemplarId)}`, {
    method: "PUT", data: datos,
  }), 200, isEjemplar);
}

export async function retireEjemplar(libroId, ejemplarId) {
  const { status, body } = await send(`${baseUrl(libroId)}/${encodeURIComponent(ejemplarId)}`, { method: "DELETE" });
  if (status !== 204) fail(status, body);
}
