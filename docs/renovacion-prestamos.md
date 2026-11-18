# Renovación de préstamos (US09)

`POST /api/prestamos/{id}/renovacion`

Requiere una sesión de estudiante y la cabecera `X-CSRF-TOKEN`, obtenida de
`GET /api/auth/csrf` después del login. No necesita cuerpo: el estudiante se
obtiene de la sesión. El endpoint está documentado en `/swagger-ui.html`.

Respuesta `200` de ejemplo:

```json
{
  "id": 12,
  "estado": "ACTIVO",
  "numeroRenovaciones": 1,
  "fechaVencimiento": "2027-01-17T10:00:00"
}
```

Cada renovación suma 30 días al vencimiento anterior. Se permiten dos como
máximo. No cambia la fecha del préstamo ni el estado del ejemplar. El nuevo
vencimiento también aparece en `GET /api/prestamos/mios`.

| HTTP | Código | Motivo |
|---|---|---|
| 401 | — | Falta la sesión |
| 403 | — | Rol distinto de estudiante o token CSRF ausente/inválido |
| 404 | `PRESTAMO_NO_ENCONTRADO` | Préstamo inexistente o de otro estudiante |
| 409 | `PRESTAMO_NO_ACTIVO` | Préstamo devuelto o cancelado |
| 409 | `PRESTAMO_VENCIDO` | Se ha alcanzado el vencimiento |
| 409 | `LIMITE_RENOVACIONES` | Ya se ha renovado dos veces |
| 409 | `ESTUDIANTE_BLOQUEADO` | Cuenta inactiva u otro préstamo activo vencido, reutilizando el bloqueo de US07 |
| 409 | `LISTA_ESPERA_PENDIENTE` | Hay solicitudes pendientes del mismo libro y ningún ejemplar libre |

Los errores de negocio conservan el formato `message`, `codigo` y `errors`.
El filtro CSRF puede responder 403 antes de comprobar una sesión anónima.

## Persistencia y concurrencia

La migración V11 añade `numero_renovaciones` con valor inicial 0, también para
los préstamos existentes, y un CHECK entre 0 y 2. Añade el estado `CANCELADO`,
sin fecha de devolución; la cancelación se reconoce al validar la renovación,
pero esta tarea no incluye un endpoint para cancelar préstamos. Las consultas
de disponibilidad solo consideran los préstamos `ACTIVO`.

`PrestamoService.renovar` es transaccional y reutiliza los repositorios de
US07. Bloquea las filas del usuario, el libro y el préstamo, en ese orden.
Comprueba las condiciones después de adquirir los bloqueos. Así, dos
peticiones del estudiante no leen el mismo contador para actualizarlo: el
contador y el vencimiento se guardan juntos al confirmar la transacción.
El orden es compatible con el de las devoluciones de US08.

## Consulta de la lista de espera de US10

US10 todavía no tiene gestión de solicitudes en este repositorio. Para hacer
la comprobación real, V12 crea el registro mínimo `solicitudes_espera`, con
libro, estudiante, fecha de solicitud y estado (`PENDIENTE`, `ASIGNADA` o
`CANCELADA`). `SolicitudEsperaRepository` consulta las pendientes de ese libro.
La renovación se rechaza únicamente si existen pendientes y la consulta de
ejemplares prestables de US07 no encuentra ninguno.

Un ejemplar libre debe estar activo, `DISPONIBLE` y sin préstamo activo. Las
solicitudes de otros libros, canceladas o ya asignadas no bloquean. Si hay
otra copia libre, se permite renovar aunque existan solicitudes pendientes.

La futura implementación de US10 debe reutilizar este registro y adquirir el
bloqueo de la fila del libro antes de modificar sus solicitudes, siguiendo el
orden usuario → libro cuando necesite ambos. El alta, la cancelación y la
asignación FIFO de solicitudes corresponden a US10.

## Interfaz del estudiante

La página React «Mis préstamos» reutiliza las tarjetas y los estilos de US08.
Cada préstamo activo muestra el vencimiento, las renovaciones utilizadas y las
restantes. El listado `GET /api/prestamos/mios` incluye ahora
`numeroRenovaciones` para mostrar el contador guardado.

«Renovar préstamo» pide confirmación antes de enviar. «Cancelar» no envía
ninguna petición. La renovación y la devolución comparten el servicio HTTP y
el bloqueo de acciones mientras se procesa una operación. Al llegar a dos
renovaciones, el botón se desactiva con un mensaje explicativo.

Después de la operación se vuelve a consultar el listado para actualizar el
vencimiento y el contador. Los rechazos muestran el motivo del backend. Si no
se puede confirmar el resultado, se consulta su estado sin reenviar la
renovación. Si falla la recarga, no se muestran acciones hasta reintentar.
Una respuesta 401 expira la sesión.

## Pruebas

`RenovacionIntegrationTests` comprueba el endpoint, las reglas, los permisos,
CSRF, Swagger, la persistencia y los límites en la base de datos. Incluye tres
renovaciones simultáneas (solo dos se admiten), renovación frente a devolución
y rollback conjunto del contador y el vencimiento.

Desde `backend`:

```powershell
.\mvnw.cmd test
```

Validación del 18/11/2026: suite completa en H2, 165 pruebas, sin fallos ni
errores y 1 omitida (exclusiva de PostgreSQL). En PostgreSQL 17 aislado, 88
pruebas de renovaciones, préstamos, devoluciones, persistencia y gestión de
ejemplares, todas correctas. Las 22 pruebas de renovación se incluyen en ambas
ejecuciones. También se volvió a comprobar en H2 la renovación y la prueba de
persistencia ajustada para respetar el índice único de PostgreSQL.

Validación de la interfaz del 18/11/2026: 123 pruebas Node y build de Vite
correctos; 43 pruebas de préstamos y renovaciones en H2 correctas. El escenario
`frontend/tests/renewLoan.browser.mjs` pasó en Chrome con respuestas HTTP
simuladas: confirmación, cancelación, contadores, doble clic, rechazos,
resultado incierto, fallo de recarga, devolución y acceso por rol. Se comprobó
el diseño a 320, 768 y 1280 px sin desbordamiento horizontal. Esta comprobación
de navegador no constituye un recorrido completo con PostgreSQL real.

La validación posterior del mismo día sí completó el recorrido real con React,
Spring Boot y PostgreSQL 17.11: 14 grupos de comprobaciones correctos, incluidas
las reservas pendientes con y sin copias libres y tres solicitudes HTTP
simultáneas. Resultados y procedimiento reproducible en
[validacion-renovaciones.md](validacion-renovaciones.md).
