// Recibe una Page de Playwright del entorno; no añade dependencias al proyecto.
export default async function validateRenewal(page, baseUrl = "http://127.0.0.1:5176", screenshotDirectory) {
  const checks = [];
  const calls = [];
  const protocolErrors = [];
  const originalViewport = page.viewportSize();
  let role = "STUDENT";
  let readStatus = 200;
  let writeStatus = 200;
  let message = "";
  let releaseWrite;
  let writeGate;
  let dropResponse = false;
  const loan = (id, titulo) => ({ id, libroId: id, titulo, fechaPrestamo: "2026-11-01T10:00:00",
    fechaVencimiento: "2026-12-01T10:00:00", fechaDevolucion: null, estado: "ACTIVO", numeroRenovaciones: 0 });
  const data = { activos: [loan(1, "Don Quijote"), loan(2, "Otro libro")],
    historial: [{ ...loan(3, "Libro devuelto"), estado: "DEVUELTO", fechaDevolucion: "2026-11-10T10:00:00" },
      { ...loan(4, "Libro cancelado"), estado: "CANCELADO" }] };
  const assert = (condition, text) => { if (!condition) throw new Error(text); };
  const card = (title) => page.getByRole("article").filter({ has: page.getByRole("heading", { name: title, exact: true }) });
  const renewButton = (title) => card(title).getByRole("button", { name: `Renovar préstamo «${title}»`, exact: true });
  const confirm = (title) => card(title).getByRole("button", { name: "Sí, renovar", exact: true });
  const ready = () => renewButton("Otro libro").waitFor();

  const routeHandler = async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: 1, nombre: "Estudiante", email: "test@example.com", roles: [role],
    } });
    if (path === "/api/auth/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "token-prueba" } });
    if (path === "/api/prestamos/mios") return route.fulfill({ status: readStatus,
      json: readStatus === 200 ? data : { message: "Fallo de consulta" } });
    if (path.endsWith("/renovacion") || path.endsWith("/devolucion")) {
      calls.push(path);
      if (request.method() !== "POST" || request.headers()["x-csrf-token"] !== "token-prueba" || request.postData()) {
        protocolErrors.push("La operación debe usar POST y CSRF, sin cuerpo");
      }
      if (writeGate) await writeGate;
      if (writeStatus !== 200) return route.fulfill({ status: writeStatus, json: { message, codigo: "RECHAZADO" } });
      const id = Number(path.split("/").at(-2));
      const current = data.activos.find((item) => item.id === id);
      if (path.endsWith("/renovacion")) {
        current.numeroRenovaciones++;
        current.fechaVencimiento = current.numeroRenovaciones === 1 ? "2026-12-31T10:00:00" : "2027-01-30T10:00:00";
        if (dropResponse) return route.abort("failed");
        return route.fulfill({ json: { id, estado: "ACTIVO", numeroRenovaciones: current.numeroRenovaciones,
          fechaVencimiento: current.fechaVencimiento } });
      }
      current.estado = "DEVUELTO";
      current.fechaDevolucion = "2026-11-18T12:00:00";
      data.activos = data.activos.filter((item) => item.id !== id);
      data.historial.push(current);
      return route.fulfill({ json: { id, estado: current.estado, fechaDevolucion: current.fechaDevolucion } });
    }
    protocolErrors.push(`Petición inesperada: ${path}`);
    return route.fulfill({ status: 500, json: { message: "Petición inesperada" } });
  };
  await page.route("**/api/**", routeHandler);
  try {
    await page.goto(`${baseUrl}/mis-prestamos`);
    await ready();
    await card("Don Quijote").getByText("Fecha de vencimiento: 01/12/2026", { exact: true }).waitFor();
    await card("Don Quijote").getByText("Renovaciones utilizadas: 0 de 2", { exact: true }).waitFor();
    await card("Don Quijote").getByText("Renovaciones restantes: 2", { exact: true }).waitFor();
    assert(await card("Libro devuelto").getByRole("button").count() === 0, "El historial no debe tener acciones");
    assert(await card("Libro cancelado").getByRole("button").count() === 0, "Los cancelados no deben tener acciones");
    checks.push("Préstamos activos, vencimientos, contadores e historial");

    await renewButton("Don Quijote").click();
    await page.getByText("¿Renovar «Don Quijote» por 30 días?", { exact: true }).waitFor();
    assert(calls.length === 0, "Pedir confirmación no debe enviar la renovación");
    await card("Don Quijote").getByRole("button", { name: "Cancelar", exact: true }).click();
    assert(calls.length === 0, "Cancelar no debe enviar la renovación");
    checks.push("Confirmación y cancelación sin peticiones");

    writeGate = new Promise((resolve) => { releaseWrite = resolve; });
    await renewButton("Don Quijote").click();
    const writeStarted = page.waitForRequest((request) => request.url().endsWith("/1/renovacion"));
    await confirm("Don Quijote").evaluate((button) => { button.click(); button.click(); });
    await writeStarted;
    await card("Don Quijote").getByRole("button", { name: "Renovando…", exact: true }).waitFor();
    assert(await page.locator(".mis-prestamos button:enabled").count() === 0, "Todas las acciones deben bloquearse durante el envío");
    assert(calls.length === 1, "El doble clic solo debe enviar una petición");
    writeGate = null;
    releaseWrite();
    await ready();
    await card("Don Quijote").getByText("Fecha de vencimiento: 31/12/2026", { exact: true }).waitFor();
    await card("Don Quijote").getByText("Renovaciones utilizadas: 1 de 2", { exact: true }).waitFor();
    await card("Don Quijote").getByText("Renovaciones restantes: 1", { exact: true }).waitFor();
    await page.getByText("Préstamo de «Don Quijote» renovado hasta el 31/12/2026.", { exact: true }).waitFor();
    checks.push("Doble clic protegido, acciones bloqueadas y actualización del vencimiento y contador");

    await renewButton("Don Quijote").click();
    await confirm("Don Quijote").click();
    await card("Don Quijote").getByText("Renovaciones restantes: 0", { exact: true }).waitFor();
    assert(await renewButton("Don Quijote").isDisabled(), "El límite debe desactivar la renovación");
    await card("Don Quijote").getByText("No quedan renovaciones disponibles.", { exact: true }).waitFor();
    checks.push("Segunda renovación y límite visible");

    writeStatus = 409;
    for (message of ["Hay solicitudes pendientes para este libro y no quedan ejemplares libres.",
      "No puedes renovar un préstamo vencido.", "No puedes renovar mientras estés bloqueado."]) {
      await renewButton("Otro libro").click();
      await confirm("Otro libro").click();
      await page.getByRole("alert").filter({ hasText: message }).waitFor();
      await ready();
      await card("Otro libro").getByText("Renovaciones utilizadas: 0 de 2", { exact: true }).waitFor();
    }
    checks.push("Rechazos por espera, vencimiento y bloqueo sin alterar contadores");

    writeStatus = 200;
    dropResponse = true;
    await renewButton("Otro libro").click();
    await confirm("Otro libro").click();
    await page.getByRole("alert").filter({ hasText: "Comprobaremos su estado" }).waitFor();
    await ready();
    await card("Otro libro").getByText("Renovaciones utilizadas: 1 de 2", { exact: true }).waitFor();
    dropResponse = false;
    checks.push("Resultado incierto: consulta el estado guardado y no reenvía la operación");

    readStatus = 500;
    await renewButton("Otro libro").click();
    await confirm("Otro libro").click();
    await page.getByRole("button", { name: "Reintentar", exact: true }).waitFor();
    assert(await page.getByRole("button", { name: /Renovar préstamo/ }).count() === 0, "No debe operar con una recarga fallida");
    readStatus = 200;
    await page.getByRole("button", { name: "Reintentar", exact: true }).click();
    await ready();
    await card("Otro libro").getByText("Renovaciones utilizadas: 2 de 2", { exact: true }).waitFor();
    checks.push("Recarga fallida bloquea las acciones hasta reintentar");

    for (const width of [320, 768, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `Desbordamiento a ${width}px`);
      if (screenshotDirectory) await page.screenshot({ path: `${screenshotDirectory}/us09-renovacion-${width}.png`, fullPage: true });
    }
    checks.push("Diseño responsive a 320, 768 y 1280 px sin desbordamiento");

    await card("Otro libro").getByRole("button", { name: "Devolver «Otro libro»", exact: true }).click();
    await card("Otro libro").getByRole("button", { name: "Sí, devolver", exact: true }).click();
    await page.getByText("Devolución de «Otro libro» registrada el 18/11/2026.", { exact: true }).waitFor();
    await card("Otro libro").getByText("Estado: DEVUELTO", { exact: true }).waitFor();
    assert(await card("Otro libro").getByRole("button").count() === 0, "La devolución debe conservar el historial sin acciones");
    checks.push("La devolución existente sigue funcionando después de renovar");

    role = "LIBRARIAN";
    await page.reload();
    await page.getByText("Solo los estudiantes pueden consultar sus préstamos.", { exact: true }).waitFor();
    assert(await page.getByRole("button", { name: /Renovar préstamo/ }).count() === 0, "Un bibliotecario no debe poder renovar");
    checks.push("Acceso limitado a estudiantes");
    assert(protocolErrors.length === 0, protocolErrors.join("; "));
    return { checks, renewalRequests: calls.filter((path) => path.endsWith("/renovacion")).length };
  } finally {
    releaseWrite?.();
    await page.unroute("**/api/**", routeHandler);
    await page.goto("about:blank");
    if (originalViewport) await page.setViewportSize(originalViewport);
  }
}
