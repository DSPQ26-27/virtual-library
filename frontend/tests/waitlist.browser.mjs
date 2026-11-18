// Recibe una Page de Playwright, igual que los otros escenarios del proyecto.
export default async function validateWaitlist(page, baseUrl) {
  let solicitudes = [];
  let errorConsulta = false;
  let errorAlta = true;
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: 1, nombre: "Estudiante", email: "prueba@example.com", roles: ["STUDENT"],
    } });
    if (path === "/api/auth/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "prueba" } });
    if (path === "/api/libros/categorias") return route.fulfill({ json: [] });
    if (path === "/api/libros") return route.fulfill({ json: {
      content: [{ id: 25, titulo: "Libro ocupado", autor: "Autor", disponible: false },
        { id: 26, titulo: "Libro libre", autor: "Autor", disponible: true }],
      page: 0, size: 20, totalElements: 2, totalPages: 1,
    } });
    if (path === "/api/espera/mias") return route.fulfill(errorConsulta
      ? { status: 500, json: { message: "Error de consulta" } } : { json: solicitudes });
    if (path === "/api/espera") {
      if (errorAlta) {
        errorAlta = false;
        return route.fulfill({ status: 409, json: { message: "Ya estás en la lista de espera de este libro." } });
      }
      solicitudes = [{ id: 7, libroId: 25, titulo: "Libro ocupado", fechaSolicitud: "2026-11-18T12:00:00", estado: "PENDIENTE" }];
      return route.fulfill({ status: 201, json: solicitudes[0] });
    }
    if (path === "/api/espera/7/cancelacion") {
      solicitudes[0].estado = "CANCELADA";
      return route.fulfill({ json: solicitudes[0] });
    }
    throw new Error(`Ruta inesperada ${path}`);
  });

  await page.goto(`${baseUrl}/catalogo`);
  const boton = page.getByRole("button", { name: /Apuntarme a la lista de espera de/ });
  await boton.waitFor();
  assert(await boton.count() === 1, "Solo los libros sin disponibilidad muestran lista de espera");
  await boton.click();
  await page.getByText("Ya estás en la lista de espera de este libro.").waitFor();
  await boton.click();
  await page.getByText("Te has apuntado. Puedes consultar la solicitud en Mis reservas.").waitFor();
  assert(await boton.isDisabled(), "El alta confirmada debe desactivar el botón");
  await page.getByRole("link", { name: "Mis reservas", exact: true }).click();
  await page.getByRole("button", { name: /Cancelar reserva de/ }).click();
  await page.getByText("Estado: CANCELADA", { exact: true }).waitFor();
  solicitudes[0].estado = "ASIGNADA";
  await page.getByRole("button", { name: "Actualizar", exact: true }).click();
  await page.getByRole("link", { name: "Consultar préstamo asignado" }).waitFor();
  errorConsulta = true;
  await page.getByRole("button", { name: "Actualizar", exact: true }).click();
  await page.getByRole("alert").filter({ hasText: "Error de consulta" }).waitFor();
  errorConsulta = false;
  solicitudes = [];
  await page.getByRole("button", { name: "Actualizar", exact: true }).click();
  await page.getByText("No tienes solicitudes de reserva.").waitFor();
  return "Alta, duplicado, cancelación, asignación, recuperación de error y estado vacío verificados";
}
