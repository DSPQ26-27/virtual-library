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
