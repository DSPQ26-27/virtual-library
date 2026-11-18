# Lista de espera y reservas en React — tarea #68

## Pantallas

El catálogo muestra «Apuntarme a la lista de espera» en libros sin ejemplares
disponibles para estudiantes. Mientras envía la petición desactiva el botón.
Después de confirmar el alta, muestra el resultado y mantiene el botón desactivado.
Si la respuesta es incierta, indica consultar Mis reservas antes de repetir.

Mis reservas está en /mis-reservas y usa StudentRoute. Muestra título, fecha y
estado de cada solicitud propia. Actualizar vuelve a consultar al servidor.
Las solicitudes PENDIENTES pueden cancelarse. Las ASIGNADAS enlazan Mis préstamos.
Las CANCELADAS se conservan en el historial. La pantalla incluye estados de
carga, error y lista vacía. Los errores de sesión hacen volver al inicio de sesión.

## Archivos principales

- WaitlistButton recibe el libro y envía el alta.
- MisReservasPage consulta y muestra las solicitudes y permite cancelarlas.
- services/waitlist.js concentra las llamadas HTTP con sesión, CSRF y timeout.
- App añade el enlace del menú y la ruta protegida.
- CatalogPage incorpora el botón y reutiliza los estilos existentes.

## Cancelación en la API

Se añade POST /api/espera/{id}/cancelacion, necesario para gestionar reservas.
Requiere sesión de estudiante y CSRF. Busca la solicitud por id y usuario de
la sesión para evitar cancelar solicitudes ajenas.

Las solicitudes ajenas o inexistentes devuelven HTTP 404. Las ASIGNADAS devuelven
409, SOLICITUD_ASIGNADA. Cancelar una PENDIENTE la pasa a CANCELADA. Repetir la
cancelación devuelve CANCELADA. Así un reintento conserva un resultado consistente.

La cancelación usa la coordinación transaccional de #67. Si compite con una
devolución, la primera operación que confirma determina si se cancela o se asigna.

## Comprobación

Desde frontend, ejecutar node --test tests/*.test.js, el lint y la compilación
de Vite. waitlist.test.js comprueba las llamadas de alta, consulta, cancelación,
CSRF y errores. waitlist.browser.mjs recibe una Page de Playwright y una URL de
Vite para recorrer la interfaz con respuestas simuladas, como los otros escenarios
de navegador existentes.

Desde backend, ejecutar .\mvnw.cmd test. Se añaden dos pruebas de cancelación
que cubren propiedad de la solicitud, roles, sesión, CSRF, reintentos, conservación
del historial y rechazo de solicitudes ya asignadas.

Esta rama parte de feature/67-asignacion-fifo. Mientras #67 esté pendiente, un
Pull Request hacia esa rama permite revisar solo #68. Una vez integradas las
dependencias, el destino puede cambiarse a main.
