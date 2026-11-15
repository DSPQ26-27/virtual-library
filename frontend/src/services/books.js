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

function isBookPage(page) {
  return (
    page !== null &&
    typeof page === "object" &&
    !Array.isArray(page) &&
    Array.isArray(page.content) &&
    page.content.every(isBook) &&
    new Set(page.content.map((book) => book.id)).size === page.content.length &&
    Number.isSafeInteger(page.page) && page.page >= 0 &&
    Number.isSafeInteger(page.size) && page.size > 0 &&
    Number.isSafeInteger(page.totalElements) && page.totalElements >= 0 &&
    Number.isSafeInteger(page.totalPages) && page.totalPages >= 0 &&
    page.content.length <= page.size &&
    page.content.length <= page.totalElements &&
    page.totalPages === Math.ceil(page.totalElements / page.size)
  );
}

export async function getBooks({ q, categoria, vista, page, size, sort, signal } = {}) {
  const params = new URLSearchParams();

  for (const [name, value] of Object.entries({ q, categoria, vista, page, size, sort })) {
    if (value === undefined || value === null) continue;
    const text = String(value).trim();
    if (text !== "") params.set(name, text);
  }

  const query = params.toString();
  const url = query ? `/api/libros?${query}` : "/api/libros";
  return requestCatalog(url, signal, isBookPage);
}

export async function getCategories({ signal } = {}) {
  return requestCatalog("/api/libros/categorias", signal, (categories) =>
    Array.isArray(categories) &&
    categories.every((category) => typeof category === "string" && category.trim().length > 0)
  );
}

// Ambos endpoints comparten sesión, timeout, cancelación y tratamiento de errores.
async function requestCatalog(url, signal, isValid) {
  const timeout = AbortSignal.timeout(10000);

  const combinedSignal = signal
    ? AbortSignal.any([signal, timeout])
    : timeout;

  try {
    const response = await fetch(url, {
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
        400: "Parámetro de búsqueda no válido.",
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
    let data;

    try {
      data = JSON.parse(text);
    } catch {
      throw new CatalogError(
        "La respuesta del catálogo no tiene el formato esperado."
      );
    }

    if (!isValid(data)) {
      throw new CatalogError(
        "La respuesta del catálogo no tiene el formato esperado."
      );
    }

    return data;
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
