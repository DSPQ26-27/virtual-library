export class CatalogError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "CatalogError";
    this.status = status;
  }
}

function isBook(book) {
  return (
    book &&
    Number.isSafeInteger(book.id) &&
    book.id > 0 &&
    typeof book.titulo === "string" &&
    book.titulo.trim().length > 0 &&
    typeof book.autor === "string" &&
    book.autor.trim().length > 0 &&
    typeof book.disponible === "boolean"
  );
}

export async function getBooks({ signal } = {}) {
  const timeout = AbortSignal.timeout(10000);

  const combinedSignal = signal
    ? AbortSignal.any([signal, timeout])
    : timeout;

  try {
    const response = await fetch("/api/libros", {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "include",
      cache: "no-store",
      redirect: "error",
      signal: combinedSignal,
    });

    if (response.status !== 200) {
      const messages = {
        401: "Tu sesión ha caducado. Inicia sesión de nuevo.",
        403: "No tienes permiso para consultar el catálogo.",
        404: "El servicio de catálogo todavía no está disponible.",
      };

      throw new CatalogError(
        messages[response.status] ||
          "No se ha podido cargar el catálogo. Inténtalo más tarde.",
        response.status
      );
    }

    const text = await response.text();
    let books;

    try {
      books = JSON.parse(text);
    } catch {
      throw new CatalogError(
        "La respuesta del catálogo no tiene el formato esperado."
      );
    }

    if (
      !Array.isArray(books) ||
      !books.every(isBook) ||
      new Set(books.map((book) => book.id)).size !== books.length
    ) {
      throw new CatalogError(
        "La respuesta del catálogo no tiene el formato esperado."
      );
    }

    return books;
  } catch (error) {
    if (signal?.aborted) throw error;

    if (error instanceof CatalogError) throw error;

    throw new CatalogError(
      error.name === "TimeoutError" || error.name === "AbortError"
        ? "El servidor tarda demasiado en responder. Inténtalo más tarde."
        : "No se ha podido conectar con el servicio de catálogo."
    );
  }
}