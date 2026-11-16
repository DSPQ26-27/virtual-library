# Gestión de ejemplares — US06

## Funcionalidad

Desde el enlace **Ejemplares** de cada libro del catálogo se abre
`/libros/:id/ejemplares`. La pantalla muestra el título, los identificadores,
códigos y estados de sus ejemplares, el total registrado y los disponibles.

Los usuarios con sesión pueden consultar. Bibliotecarios y administradores pueden
registrar y editar con un único formulario, y retirar tras confirmar. Se reutilizan
los estilos existentes, con mensajes de carga, error, reintento y éxito.

El código es obligatorio, único en toda la biblioteca y de hasta 50 caracteres.
Se recortan sus espacios exteriores. El identificador y el libro asociado no se
modifican al editar.

## Cambios para completar los criterios del backend

La primera implementación permitía retirar ejemplares en estado `PRESTADO` y
todavía no tenía registros de préstamos. Se ha completado con lo siguiente:

- Rechazo con HTTP 409 al retirar un ejemplar prestado o con un préstamo activo.
- Consulta de los registros reales de préstamos, aunque el estado almacenado del
  ejemplar esté desincronizado y diga `DISPONIBLE`.
- Rechazo con HTTP 409 al intentar marcar como disponible un ejemplar con un
  préstamo activo. Editar su código manteniendo `PRESTADO` sigue permitido.
- Conservación del historial al retirar, con el usuario, el ejemplar y las fechas
  originales de los préstamos devueltos.
- Disponibilidad coherente en los contadores, en el booleano del catálogo y en
  la vista **Disponibles**: exige ejemplar activo, estado disponible y ausencia
  de préstamos activos.

La interfaz ya muestra los mensajes 409 del servidor sin perder el formulario ni
alterar la lista o sus contadores. Se mantiene visible la acción de retirada para
que el rechazo explique el motivo.

## Estados y retirada lógica

Se conserva el modelo existente:

| Situación | Representación |
|---|---|
| Disponible | `activo=true`, `estado=DISPONIBLE` y ningún préstamo activo |
| Prestado | `estado=PRESTADO` o registro de préstamo pendiente de devolución |
| Retirado | `activo=false` |

Un préstamo activo prevalece al consultar: se muestra `PRESTADO` aunque la columna
del ejemplar indique `DISPONIBLE`. Esto no modifica datos durante una consulta.

Retirar cambia únicamente `activo` a `false`. Conserva la fila, su identificador,
código, estado y relaciones. El código sigue reservado. Un ejemplar retirado no
puede editarse ni reactivarse desde esta función. Repetir su retirada responde
204 sin cambiar datos, incluidos los ejemplares retirados de versiones anteriores.

El **total incluye los retirados**; la pantalla lo indica. Los disponibles nunca
incluyen retirados ni ejemplares con préstamos activos.

Editar y retirar utilizan el bloqueo de escritura existente sobre el ejemplar,
dentro de una transacción, para evitar que una edición simultánea lo reactive.

## Registro mínimo de préstamos

La nueva migración `V8__crear_prestamos.sql` añade la tabla `prestamos`:

| Campo | Uso |
|---|---|
| `id` | Identificador del préstamo |
| `ejemplar_id` | Referencia obligatoria al ejemplar |
| `usuario_id` | Referencia obligatoria al usuario |
| `fecha_prestamo` | Fecha de inicio |
| `fecha_devolucion` | Nula mientras el préstamo está activo |

Incluye un índice por ejemplar y fecha de devolución. Las relaciones no tienen
borrado en cascada. La retirada de un ejemplar no ejecuta ningún DELETE sobre
ejemplares ni préstamos.

`Prestamo.java` y `PrestamoRepository.java` aportan la persistencia y las consultas
necesarias. `devolver()` registra la fecha conservando la primera si se repite.
Los ejemplares antiguos marcados `PRESTADO` también bloquean la retirada, aunque
no tengan un registro histórico: no se inventan usuarios ni préstamos al migrar.

Esta base cubre los criterios de gestión de ejemplares. El préstamo automático,
la devolución desde la web, renovaciones y reservas corresponden al Sprint 3.
Sus futuras operaciones deberán actualizar el ejemplar y el préstamo en una misma
transacción, usando el bloqueo del ejemplar compartido con la gestión del catálogo.

## API y permisos

| Método y ruta | Éxito |
|---|---|
| `GET /api/libros/{libroId}/ejemplares` | 200: listado y contadores |
| `POST /api/libros/{libroId}/ejemplares` | 201: ejemplar creado |
| `PUT /api/libros/{libroId}/ejemplares/{ejemplarId}` | 200: ejemplar actualizado |
| `DELETE /api/libros/{libroId}/ejemplares/{ejemplarId}` | 204: retirada lógica |

Alta y edición reciben `{ "codigo": "EJ-0005", "estado": "DISPONIBLE" }`.
El estado editable puede ser `DISPONIBLE` o `PRESTADO`; retirar usa DELETE.

GET devuelve `{ "ejemplares": [...], "total": 1, "disponibles": 1 }`.
Cada ejemplar tiene `id`, `codigo`, `estado` y `activo`. POST y PUT devuelven ese
objeto individual. Los errores conservan el formato `{ "message": "...", "errors": {} }`.

Se reutilizan las reglas existentes de Spring Security: sesión para consultar,
roles `LIBRARIAN` o `ADMIN` y token `X-CSRF-TOKEN` para escribir. La API devuelve
400 por datos inválidos, 401 sin sesión, 403 sin permiso o CSRF válido, 404 si no
existe el recurso o la asociación libro/ejemplar y 409 por conflictos.

Al rechazar una retirada por préstamo activo responde:

```json
{
  "message": "No se puede retirar un ejemplar con un préstamo activo.",
  "errors": {}
}
```

## Archivos principales

- Frontend: `EjemplaresPage.jsx`, `EjemplaresPage.css`, `services/ejemplares.js`,
  ruta en `App.jsx` y enlace en `CatalogPage.jsx`.
- Backend: `EjemplarGestionController`, `EjemplarGestionService`, DTO de entrada
  y salida, validaciones y `EjemplarGestionExceptionHandler`.
- Protección de préstamos: `EjemplarConPrestamoActivoException`, paquete
  `prestamo` y migración V8.
- Disponibilidad: `EjemplarRepository` y `LibroSpecifications`.
- Pruebas: `EjemplarGestionIntegrationTests.java`, `ejemplares.test.js` y
  `ejemplares.browser.mjs`.

No se han añadido dependencias. Para usar la nueva tabla, reiniciar el backend:
Flyway aplicará V8 sobre la base configurada, conservando los datos existentes.

## Comprobación manual

1. Como bibliotecario o administrador, registrar un ejemplar disponible.
2. Editarlo a `PRESTADO`, intentar retirarlo y confirmar: debe aparecer el error,
   conservarse activo y mantenerse los contadores.
3. Para ese ejemplar de prueba sin préstamo real registrado, cambiar a
   `DISPONIBLE` y retirar: debe quedar identificado como retirado.
4. Como estudiante, comprobar que solo se permite consultar.

Las comprobaciones con registros reales de préstamos e historial se realizan en
las pruebas de integración, usando H2 aislada. La web de préstamos pertenece al
Sprint 3.

## Validación

Comprobaciones realizadas el 16/11/2026:

| Comprobación | Resultado |
|---|---|
| Backend: `.\mvnw.cmd -B test` | 104 pruebas correctas, incluidas 27 de gestión de ejemplares |
| Frontend: `node --test tests/*.test.js` | 96 pruebas correctas |
| Frontend: `npm.cmd run lint` | Correcto |
| Frontend: `npm.cmd run build` | Correcto |
| Navegador: `tests/ejemplares.browser.mjs` | 13 grupos de comprobaciones correctos |
| Revisión de código y `git diff --check` | Sin hallazgos bloqueantes ni errores de espacios |

Las nuevas pruebas de Spring usan registros de préstamos persistidos en H2 y
comprueban el rechazo de retirada por ambos roles, incluso con estado
desincronizado; el rechazo de una falsa devolución mediante edición; la
conservación del historial, usuario y fechas después de retirar; y la coherencia
entre el listado y las tres vistas del catálogo antes y después de devolver.
Se conserva la prueba concurrente de edición y retirada.

El escenario de navegador comprueba que el rechazo 409 conserva ejemplares,
contadores y formulario, muestra el motivo y no indica éxito. También cubre los
permisos, validaciones, CRUD y anchos de 1280, 768 y 320 píxeles. Usa HTTP simulado
y una `Page` de Playwright del entorno, sin añadir dependencias al proyecto:

```javascript
import validateEjemplares from './tests/ejemplares.browser.mjs';
console.log(await validateEjemplares(page, 'http://127.0.0.1:5175'));
```

La revisión incluyó esquema, relaciones, historial, transacciones, permisos y
coherencia frontend/backend. En esta revisión las pruebas del backend se han
ejecutado con H2 aislada y las de navegador con respuestas simuladas; no se ha
modificado PostgreSQL, los CSV ni `.env`.
