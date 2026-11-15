// Escenario reutilizable: recibe una Page de Playwright ya disponible en el entorno.
// No necesita importar Playwright ni añade dependencias al proyecto.
export default async function validateCatalog(page, baseUrl = "http://127.0.0.1:5174") {
  const checks = [];
  const calls = [];
  let categoryFailure = true;
  let bookFailure = true;
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  const input = page.getByLabel("Buscar por título o autor", { exact: true });
  const waitReady = () => page.waitForFunction(() =>
    document.querySelector(".catalogo__resumen")?.textContent.includes("Total de libros encontrados:"));
  const lastQuery = () => new URL(calls.filter((url) => url.pathname === "/api/libros").at(-1)).searchParams;

  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    calls.push(url);
    if (url.pathname === "/api/auth/me") {
      return route.fulfill({ json: { id: 1, nombre: "Alumno", email: "test@example.com", roles: ["STUDENT"] } });
    }
    if (url.pathname === "/api/libros/categorias") {
      return categoryFailure
        ? route.fulfill({ status: 500, json: {} })
        : route.fulfill({ json: ["Historia", "Informática"] });
    }
    assert(url.pathname === "/api/libros", "Endpoint inesperado");
    assert(url.searchParams.get("size") === "20", "Debe pedir solo 20 libros");
    assert(url.searchParams.get("sort") === "titulo,asc", "Debe ordenar por título");
    const q = url.searchParams.get("q");
    if (q === "error" && bookFailure) {
      bookFailure = false;
      return route.fulfill({ status: 500, json: {} });
    }
    if (q === "prohibido") return route.fulfill({ status: 403, json: {} });
    if (q === "sesion") return route.fulfill({ status: 401, json: {} });
    const current = Number(url.searchParams.get("page"));
    const total = q === "sinresultados" ? 0 : 41;
    return route.fulfill({ json: {
      content: Array.from({ length: Math.max(0, Math.min(20, total - current * 20)) }, (_, i) => ({
        id: current * 20 + i + 1, titulo: `Libro ${q || "general"} ${current}-${i}`,
        autor: "Autor", disponible: i % 2 === 0,
      })),
      page: current, size: 20, totalElements: total, totalPages: Math.ceil(total / 20),
    } });
  });

  try {
    await page.goto(`${baseUrl}/catalogo`);
    await waitReady();
    await page.getByRole("button", { name: "Reintentar categorías" }).waitFor();
    checks.push("Fallo de categorías no bloquea libros");
    categoryFailure = false;
    await page.getByRole("button", { name: "Reintentar categorías" }).click();
    await page.getByLabel("Categoría", { exact: true }).selectOption("Historia");
    await waitReady();
    const before = calls.length;
    await input.fill("Cervantes & García");
    assert(calls.length === before, "Escribir no debe consultar");
    await input.press("Enter");
    await waitReady();
    await input.fill("Texto sin aplicar");
    await page.getByRole("radio", { name: "Disponibles", exact: true }).check();
    await waitReady();
    assert(lastQuery().get("q") === "Cervantes & García", "La vista debe conservar el texto aplicado");
    assert(lastQuery().get("categoria") === "Historia", "Debe conservar la categoría");
    checks.push("Texto separado, Enter, codificación y filtros combinados");

    await page.getByRole("button", { name: "Siguiente", exact: true }).click();
    await waitReady();
    assert(lastQuery().get("page") === "1", "La segunda página interna es 1");
    assert(await page.locator(".catalogo__resumen").textContent().then((s) => s.includes("Página 2 de 3")), "Numeración visible");
    await page.getByLabel("Categoría", { exact: true }).selectOption("Informática");
    await waitReady();
    assert(lastQuery().get("page") === "0", "El filtro debe reiniciar la página");
    await page.getByRole("button", { name: "Limpiar filtros" }).click();
    await waitReady();
    assert(await input.inputValue() === "", "Limpiar debe borrar el borrador");
    assert(!lastQuery().has("q") && !lastQuery().has("categoria") && lastQuery().get("vista") === "TODOS", "Limpieza completa");
    checks.push("Paginación, reinicio y limpieza");

    await input.fill("error");
    await page.getByRole("button", { name: "Buscar", exact: true }).click();
    await page.getByRole("button", { name: "Reintentar", exact: true }).waitFor();
    const failedQuery = lastQuery().toString();
    await page.getByRole("button", { name: "Reintentar", exact: true }).click();
    await waitReady();
    assert(lastQuery().toString() === failedQuery, "Reintentar debe conservar filtros");
    await input.fill("sinresultados");
    await input.press("Enter");
    await page.getByText("No se encontraron resultados con estos filtros.").waitFor();
    checks.push("Buscar, error, reintento y cero resultados");

    // Simula un transporte que ignora AbortController para comprobar también la guarda de vigencia.
    await page.evaluate(() => {
      const original = window.fetch;
      window.fetch = (url, options) => {
        if (String(url).includes("q=antigua")) {
          return new Promise((resolve) => setTimeout(() => {
            window.__us04OldFinished = true;
            resolve(Response.json({}, { status: 401 }));
          }, 350));
        }
        return original(url, options);
      };
    });
    await input.fill("antigua");
    await input.press("Enter");
    await page.getByText("Cargando catálogo…", { exact: true }).waitFor();
    await input.fill("nueva");
    await input.press("Enter");
    await page.getByRole("heading", { name: "Libro nueva 0-0", exact: true }).waitFor();
    await page.waitForFunction(() => window.__us04OldFinished === true);
    assert(page.url().endsWith("/catalogo"), "Un 401 obsoleto no debe cerrar la sesión");
    checks.push("Carga y respuesta obsoleta ignorada, incluso un 401");

    for (const width of [1280, 768, 320]) {
      await page.setViewportSize({ width, height: 900 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Desbordamiento a ${width}px`);
    }
    await page.getByRole("button", { name: "Siguiente", exact: true }).focus();
    await page.keyboard.press("Enter");
    await page.waitForFunction(() => document.activeElement?.classList.contains("catalogo__resumen"));
    checks.push("Responsive y foco tras paginar con teclado");
    await input.fill("prohibido");
    await input.press("Enter");
    await page.getByText("No tienes permiso para consultar el catálogo.", { exact: true }).waitFor();
    assert(page.url().endsWith("/catalogo"), "403 no debe cerrar sesión");
    await input.fill("sesion");
    await input.press("Enter");
    await page.waitForURL("**/login");
    checks.push("403 conserva sesión; 401 vigente redirige al login");
    return { checks, bookRequests: calls.filter((url) => url.pathname === "/api/libros").length };
  } finally {
    // Recargar elimina el fetch simulado; about:blank evita peticiones reales al retirar rutas.
    await page.goto("about:blank");
    await page.unrouteAll({ behavior: "wait" });
  }
}
