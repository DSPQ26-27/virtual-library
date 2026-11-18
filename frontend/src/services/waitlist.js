import { csrfToken } from "./session.js";

export class WaitlistError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "WaitlistError";
    this.status = status;
  }
}

function isRequest(item) {
  return item && Number.isSafeInteger(item.id) && item.id > 0 &&
    Number.isSafeInteger(item.libroId) && item.libroId > 0 &&
    typeof item.titulo === "string" && typeof item.fechaSolicitud === "string" &&
    ["PENDIENTE", "ASIGNADA", "CANCELADA"].includes(item.estado);
}

async function request(path, { method = "GET", data, signal } = {}) {
  const headers = { Accept: "application/json" };
  if (method === "POST") headers["X-CSRF-TOKEN"] = await csrfToken();
  if (data) headers["Content-Type"] = "application/json";
  const timeout = AbortSignal.timeout(10000);
  try {
    const response = await fetch(`/api/espera${path}`, {
      method, headers, credentials: "include", cache: "no-store", redirect: "error",
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      body: data ? JSON.stringify(data) : undefined,
    });
    let body;
    try { body = await response.json(); }
    catch { throw new WaitlistError("No se pudo interpretar la respuesta. Actualiza Mis reservas para comprobar su estado.", response.status); }
    if (!response.ok) {
      throw new WaitlistError(body?.message || "No se pudo completar la operación.", response.status);
    }
    const valid = method === "GET" ? Array.isArray(body) && body.every(isRequest) : isRequest(body);
    if (!valid) throw new WaitlistError("La respuesta de reservas no tiene el formato esperado.");
    return body;
  } catch (error) {
    if (signal?.aborted || error instanceof WaitlistError) throw error;
    throw new WaitlistError("No se pudo confirmar la operación. Actualiza Mis reservas para comprobar su estado.");
  }
}

export function joinWaitlist(libroId) {
  return request("", { method: "POST", data: { libroId } });
}

export function getMyWaitlist({ signal } = {}) {
  return request("/mias", { signal });
}

export function cancelWaitlist(id) {
  return request(`/${id}/cancelacion`, { method: "POST" });
}
