import { csrfToken } from "./session.js";

/** Error al pedir un préstamo, con el código que envía el backend (US07). */
export class LoanError extends Error {
  constructor(message, status = 0, code = "") {
    super(message);
    this.name = "LoanError";
    this.status = status;
    this.code = code;
  }
}

const MESSAGES = {
  400: "Revisa los datos del préstamo.",
  401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
  403: "Solo los estudiantes pueden pedir préstamos.",
  404: "El libro no existe.",
  409: "No se ha podido realizar el préstamo.",
};

/** Comprueba que la respuesta tiene los datos que va a mostrar la pantalla. */
function isLoan(loan) {
  return (
    loan !== null && typeof loan === "object" &&
    Number.isSafeInteger(loan.id) && Number.isSafeInteger(loan.libroId) &&
    typeof loan.titulo === "string" && typeof loan.ejemplarCodigo === "string" &&
    typeof loan.fechaVencimiento === "string" && loan.estado === "ACTIVO"
  );
}

/** POST /api/prestamos: pide un libro; el backend elige el ejemplar (US07). */
export async function requestLoan(libroId) {
  const token = await csrfToken();

  let response;
  try {
    response = await fetch("/api/prestamos", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json", "X-CSRF-TOKEN": token },
      credentials: "include",
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10000),
      body: JSON.stringify({ libroId }),
    });
  } catch (error) {
    throw new LoanError(
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

  if (response.status === 201 && isLoan(body)) return body;
  if (response.status === 201) {
    throw new LoanError("La respuesta del servidor no tiene el formato esperado.", 201);
  }

  const message =
    response.status === 401 || response.status === 403
      ? MESSAGES[response.status]
      : (typeof body?.message === "string" && body.message) || MESSAGES[response.status] ||
        "No se ha podido completar el préstamo. Inténtalo más tarde.";
  const code = typeof body?.codigo === "string" ? body.codigo : "";
  throw new LoanError(message, response.status, code);
}

/** "2026-12-17T12:40:00" → "17/12/2026" (sin pasar por zonas horarias). */
export function formatDueDate(iso) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(typeof iso === "string" ? iso : "");
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

function isListedLoan(loan, estado) {
  return loan && typeof loan === "object" &&
    Number.isSafeInteger(loan.id) && loan.id > 0 &&
    Number.isSafeInteger(loan.libroId) && loan.libroId > 0 &&
    typeof loan.titulo === "string" && loan.titulo.trim().length > 0 &&
    typeof loan.fechaPrestamo === "string" && Boolean(formatDueDate(loan.fechaPrestamo)) &&
    typeof loan.fechaVencimiento === "string" && Boolean(formatDueDate(loan.fechaVencimiento)) &&
    loan.estado === estado && (estado === "ACTIVO" ? loan.fechaDevolucion == null :
      typeof loan.fechaDevolucion === "string" && Boolean(formatDueDate(loan.fechaDevolucion)));
}

/** Consulta los préstamos del estudiante de la sesión, con cancelación opcional. */
export async function getMyLoans({ signal } = {}) {
  const timeout = AbortSignal.timeout(10000);
  try {
    const response = await fetch("/api/prestamos/mios", {
      method: "GET", headers: { Accept: "application/json" }, credentials: "include",
      cache: "no-store", redirect: "error", signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    });
    if (response.status !== 200) {
      const messages = {
        401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
        403: "No tienes permiso para consultar tus préstamos.",
      };
      throw new LoanError(messages[response.status] || "No se han podido cargar tus préstamos.", response.status);
    }
    let data;
    try { data = await response.json(); }
    catch (error) {
      if (signal?.aborted || timeout.aborted) throw error;
      throw new LoanError("La respuesta de préstamos no tiene el formato esperado.");
    }
    if (!data || !Array.isArray(data.activos) || !Array.isArray(data.historial) ||
        !data.activos.every((loan) => isListedLoan(loan, "ACTIVO")) ||
        !data.historial.every((loan) => isListedLoan(loan, "DEVUELTO"))) {
      throw new LoanError("La respuesta de préstamos no tiene el formato esperado.");
    }
    return data;
  } catch (error) {
    if (signal?.aborted || error instanceof LoanError) throw error;
    throw new LoanError(timeout.aborted ? "El servidor tarda demasiado en responder. Inténtalo más tarde." :
      "No se ha podido conectar con el servidor.");
  }
}

/** Registra la devolución; el servidor identifica al estudiante por la sesión. */
export async function returnLoan(prestamoId) {
  if (!Number.isSafeInteger(prestamoId) || prestamoId <= 0) throw new LoanError("Préstamo no válido.");
  const token = await csrfToken();
  const timeout = AbortSignal.timeout(10000);
  try {
    const response = await fetch(`/api/prestamos/${prestamoId}/devolucion`, {
      method: "POST", headers: { Accept: "application/json", "X-CSRF-TOKEN": token },
      credentials: "include", cache: "no-store", redirect: "error", signal: timeout,
    });
    let body = null;
    try { body = JSON.parse(await response.text()); }
    catch (error) { if (timeout.aborted) throw error; }
    if (response.status === 200) {
      if (body?.id !== prestamoId || body.estado !== "DEVUELTO" || !formatDueDate(body.fechaDevolucion)) {
        throw new LoanError("No se pudo confirmar la respuesta de devolución.", 200);
      }
      return body;
    }
    const messages = {
      401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
      403: "No se ha podido autorizar la devolución.",
      404: "El préstamo no existe o ya no está disponible.",
      409: "Este préstamo ya ha sido devuelto.",
    };
    throw new LoanError(messages[response.status] || "No se ha podido confirmar la devolución.",
      response.status, typeof body?.codigo === "string" ? body.codigo : "");
  } catch (error) {
    if (error instanceof LoanError) throw error;
    throw new LoanError(timeout.aborted ? "La devolución ha tardado demasiado; comprobaremos su estado." :
      "No se ha podido confirmar la devolución. Comprobaremos su estado.");
  }
}
