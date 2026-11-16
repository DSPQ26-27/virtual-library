// Recibe una Page de Playwright del entorno; no añade dependencias al proyecto.
export default async function validateEjemplares(page, baseUrl = "http://127.0.0.1:5174") {
  const checks = [];
  const calls = [];
  const protocolErrors = [];
  const path = "/libros/7/ejemplares";
  const api = "/api/libros/7/ejemplares";
  const book = { id: 7, titulo: "Libro de prueba", autor: "Autora", retirado: false };
  let role = "LIBRARIAN";
  let items = [];
  let readStatus = 500;
  let writeStatus = 200;
  let nextId = 4;
  let releaseRead;
  let releaseWrite;
  let readGate = new Promise((resolve) => { releaseRead = resolve; });
  let writeGate = null;
  const originalViewport = page.viewportSize();
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  const count = (method) => calls.filter((call) => call.method === method && call.pathname.startsWith(api)).length;
  const codigo = page.getByLabel("Código del ejemplar", { exact: true });
  const estado = page.getByLabel("Estado", { exact: true });
  const register = () => page.getByRole("button", { name: "Registrar ejemplar", exact: true });
  const card = (code) => page.getByRole("listitem").filter({
    has: page.getByRole("heading", { name: `Ejemplar ${code}`, exact: true }),
  });
  const waitCounts = (total, disponibles) => page.waitForFunction(({ total, disponibles }) =>
    document.querySelector(".ejemplares .catalogo__resumen")?.textContent ===
      `Total de ejemplares: ${total} · Disponibles: ${disponibles}`, { total, disponibles });

  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    calls.push({ pathname: url.pathname, method });
    if (url.pathname === "/api/auth/me") {
      return route.fulfill({ json: { id: 1, nombre: "Personal de prueba", email: "test@example.com", roles: [role] } });
    }
    if (url.pathname === "/api/auth/csrf") {
      return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "token-prueba" } });
    }
    if (url.pathname === "/api/libros/7" && method === "GET") return route.fulfill({ json: book });
    if (url.pathname === api && method === "GET") {
      if (readGate) await readGate;
      if (readStatus !== 200) return route.fulfill({ status: readStatus, json: { message: "Fallo de consulta simulado." } });
      return route.fulfill({ json: {
        ejemplares: items,
        total: items.length,
        disponibles: items.filter((item) => item.activo && item.estado === "DISPONIBLE").length,
      } });
    }
    if (url.pathname === api || url.pathname.startsWith(`${api}/`)) {
      if (request.headers()["x-csrf-token"] !== "token-prueba") protocolErrors.push("Mutación sin token CSRF");
      if (writeGate) await writeGate;
      if (writeStatus !== 200) return route.fulfill({ status: writeStatus, json: { message: "Operación rechazada." } });
      if (method === "POST") {
        const data = request.postDataJSON();
        if (data.codigo === "DUPLICADO") return route.fulfill({ status: 409, json: {
          message: "Ya existe un ejemplar con ese código.",
          errors: { codigo: "Este código ya está registrado." },
        } });
        const created = { id: nextId++, ...data, activo: true };
        items = [...items, created];
        return route.fulfill({ status: 201, json: created });
      }
      const id = Number(url.pathname.split("/").at(-1));
      if (method === "PUT") {
        const data = request.postDataJSON();
        items = items.map((item) => item.id === id ? { ...item, ...data } : item);
        return route.fulfill({ json: items.find((item) => item.id === id) });
      }
      if (method === "DELETE") {
        items = items.map((item) => item.id === id ? { ...item, activo: false } : item);
        return route.fulfill({ status: 204 });
      }
    }
    protocolErrors.push(`Petición inesperada: ${method} ${url.pathname}`);
    return route.fulfill({ status: 500, json: { message: "Endpoint inesperado en la prueba." } });
  });

  try {
    await page.goto(`${baseUrl}${path}`);
    await page.getByText("Cargando ejemplares…", { exact: true }).waitFor();
    assert(await page.getByRole("button", { name: "Actualizar", exact: true }).isDisabled(), "Actualizar debe bloquearse durante la carga");
    readGate = null;
    releaseRead();
    await page.getByRole("button", { name: "Reintentar", exact: true }).waitFor();
    readStatus = 200;
    await page.getByRole("button", { name: "Reintentar", exact: true }).click();
    await page.getByText("Este libro todavía no tiene ejemplares.", { exact: true }).waitFor();
    await waitCounts(0, 0);
    checks.push("Carga bloqueada, error 500, reintento y lista vacía");

    const beforeInvalid = count("POST");
    await register().click();
    await page.locator("#ejemplar-codigo-error").waitFor();
    assert(await codigo.getAttribute("aria-invalid") === "true", "Código vacío debe señalarse en su campo");
    assert(await codigo.getAttribute("maxlength") === "50", "Código limitado a 50 caracteres");
    assert(count("POST") === beforeInvalid, "Validación local no debe enviar POST");
    checks.push("Validación local accesible sin petición de escritura");

    items = [
      { id: 1, codigo: "EJ-A", estado: "DISPONIBLE", activo: true },
      { id: 2, codigo: "EJ-B", estado: "PRESTADO", activo: true },
      { id: 3, codigo: "EJ-RETIRADO", estado: "DISPONIBLE", activo: false },
    ];
    await page.getByRole("button", { name: "Actualizar", exact: true }).click();
    await waitCounts(3, 1);
    assert(await card("EJ-A").textContent().then((text) => text.includes("Identificador: 1") && text.includes("Disponible")), "Debe mostrar identificador y estado");
    await card("EJ-B").getByText("Prestado", { exact: false }).waitFor();
    await card("EJ-RETIRADO").getByText("Retirado", { exact: true }).waitFor();
    assert(await card("EJ-RETIRADO").getByRole("button").count() === 0, "Retirados sin acciones de edición o retirada");
    checks.push("Lista con identificadores, estados y retirados fuera de disponibilidad");

    await codigo.fill("DUPLICADO");
    await register().click();
    await page.getByText("Este código ya está registrado.", { exact: true }).waitFor();
    assert(await codigo.inputValue() === "DUPLICADO", "409 conserva los datos del formulario");
    assert(await codigo.getAttribute("aria-describedby") === "ejemplar-codigo-error", "409 debe asociar el error al campo");
    await waitCounts(3, 1);
    checks.push("409 mostrado junto al código sin alterar datos ni contadores");

    writeGate = new Promise((resolve) => { releaseWrite = resolve; });
    await codigo.fill("EJ-NUEVO");
    const beforeSaving = count("POST");
    await register().click();
    await page.getByRole("button", { name: "Guardando…", exact: true }).waitFor();
    assert(await codigo.isDisabled() && await estado.isDisabled(), "Formulario bloqueado mientras guarda");
    assert(await page.locator(".ejemplares button").evaluateAll((buttons) => buttons.every((button) => button.matches(":disabled"))), "Gestión y actualización bloqueadas mientras guarda");
    await page.locator(".ejemplares__formulario").evaluate((form) => {
      form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    writeGate = null;
    releaseWrite();
    await page.getByText("Ejemplar «EJ-NUEVO» registrado correctamente.", { exact: true }).waitFor();
    await waitCounts(4, 2);
    assert(count("POST") === beforeSaving + 1, "No debe duplicar el alta durante el guardado");
    assert(await codigo.inputValue() === "", "Alta correcta limpia el formulario");
    checks.push("Alta, bloqueo de operaciones y doble envío, éxito y contadores actualizados");

    await card("EJ-NUEVO").getByRole("button", { name: "Editar ejemplar EJ-NUEVO", exact: true }).click();
    assert(await codigo.inputValue() === "EJ-NUEVO", "Editar carga el ejemplar en el mismo formulario");
    await codigo.fill("EJ-EDITADO");
    await estado.selectOption("PRESTADO");
    await page.getByRole("button", { name: "Guardar cambios", exact: true }).click();
    await page.getByText("Cambios del ejemplar «EJ-EDITADO» guardados correctamente.", { exact: true }).waitFor();
    await waitCounts(4, 1);
    await card("EJ-EDITADO").getByText("Prestado", { exact: false }).waitFor();
    await card("EJ-A").getByRole("button", { name: "Editar ejemplar EJ-A", exact: true }).click();
    await page.getByRole("button", { name: "Cancelar edición", exact: true }).click();
    assert(await codigo.inputValue() === "", "Cancelar edición restaura el formulario de alta");
    checks.push("Edición de código/estado, disponibilidad recalculada y cancelación de edición");

    const beforeDelete = count("DELETE");
    await card("EJ-A").getByRole("button", { name: "Retirar ejemplar EJ-A", exact: true }).click();
    await page.getByText("¿Retirar el ejemplar «EJ-A»? Se conservará su registro.", { exact: true }).waitFor();
    assert(count("DELETE") === beforeDelete, "Pedir confirmación no debe retirar");
    await card("EJ-A").getByRole("button", { name: "Cancelar", exact: true }).click();
    assert(count("DELETE") === beforeDelete, "Cancelar no debe enviar DELETE");
    await card("EJ-A").getByRole("button", { name: "Retirar ejemplar EJ-A", exact: true }).click();
    await card("EJ-A").getByRole("button", { name: "Sí, retirar", exact: true }).click();
    await page.getByText("Ejemplar «EJ-A» retirado correctamente.", { exact: true }).waitFor();
    await waitCounts(4, 0);
    await card("EJ-A").getByText("Retirado", { exact: true }).waitFor();
    assert(count("DELETE") === beforeDelete + 1 && await card("EJ-A").getByRole("button").count() === 0, "Retirada lógica confirmada una sola vez");
    checks.push("Confirmación y cancelación de retirada; registro conservado y disponibilidad actualizada");

    role = "STUDENT";
    await page.reload();
    await waitCounts(4, 0);
    assert(await page.getByRole("form").count() === 0, "Estudiante sin formulario de gestión");
    assert(await page.getByRole("button", { name: /^(Editar ejemplar|Retirar ejemplar|Registrar ejemplar)/ }).count() === 0, "Estudiante sin acciones de gestión");
    role = "ADMIN";
    await page.reload();
    await waitCounts(4, 0);
    await register().waitFor();
    assert(await card("EJ-B").getByRole("button").count() === 2, "Administrador con acciones de gestión");
    checks.push("Estudiante con lectura; bibliotecario y administrador con gestión");

    for (const width of [1280, 768, 320]) {
      await page.setViewportSize({ width, height: 900 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Desbordamiento a ${width}px`);
    }
    checks.push("Responsive sin desbordamiento en 1280, 768 y 320 px");

    writeStatus = 403;
    await codigo.fill("EJ-PROHIBIDO");
    await register().click();
    await page.getByText(/No tienes permiso/).waitFor();
    assert(new URL(page.url()).pathname === path && await codigo.inputValue() === "EJ-PROHIBIDO", "403 conserva sesión y formulario");
    readStatus = 403;
    await page.getByRole("button", { name: "Actualizar", exact: true }).click();
    await page.getByRole("button", { name: "Reintentar", exact: true }).waitFor();
    assert(new URL(page.url()).pathname === path, "403 de consulta conserva sesión");
    readStatus = 200;
    await page.getByRole("button", { name: "Reintentar", exact: true }).click();
    await waitCounts(4, 0);
    writeStatus = 401;
    await codigo.fill("EJ-SESION");
    await register().click();
    await page.waitForURL("**/login");
    checks.push("403 de consulta/escritura conserva sesión; 401 de escritura expira y redirige");
    assert(protocolErrors.length === 0, protocolErrors.join("; "));
    return { checks, requests: calls.length };
  } finally {
    releaseRead?.();
    releaseWrite?.();
    await page.goto("about:blank");
    await page.unrouteAll({ behavior: "wait" });
    if (originalViewport) await page.setViewportSize(originalViewport);
  }
}
