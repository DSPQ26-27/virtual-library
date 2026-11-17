# Contrato de préstamos

Implementado en el backend en la Issue #58 (US07 #7) y usado por el catálogo
React en la Issue #59. Documentado también en Swagger:
http://localhost:8080/swagger-ui.html, sección "Préstamos".

## Seguridad

- Requiere sesión. Sin sesión: HTTP 401.
- Solo STUDENT puede pedir préstamos. LIBRARIAN y ADMIN reciben HTTP 403.
- Exige el token CSRF (GET /api/auth/csrf) en la cabecera X-CSRF-TOKEN.
  Sin token válido: HTTP 403.
- El estudiante se toma de la sesión; no se envía en el JSON.

## Endpoint

| Petición | Qué hace | Éxito |
|---|---|---|
| POST /api/prestamos | Presta al estudiante de la sesión un ejemplar libre del libro | 201 |
| POST /api/prestamos/{id}/devolucion | Devuelve un préstamo activo del estudiante de la sesión, sin cuerpo | 200 |

Petición:

```json
{ "libroId": 1 }
```

Respuesta 201:

```json
{
  "id": 12,
  "libroId": 1,
  "titulo": "Don Quijote de la Mancha",
  "ejemplarCodigo": "EJ-0001",
  "fechaPrestamo": "2026-11-17T12:40:00",
  "fechaVencimiento": "2026-12-17T12:40:00",
  "estado": "ACTIVO"
}
```

## Reglas

- El backend elige automáticamente el ejemplar: el más antiguo que esté activo,
  DISPONIBLE y sin préstamo activo.
- El préstamo vence a los 30 días y el ejemplar pasa a PRESTADO.
- Un estudiante puede tener como máximo 5 préstamos activos.
- Un estudiante con algún préstamo activo ya vencido está bloqueado (regla de la
  US12). El bloqueo desaparece en cuanto devuelve los préstamos vencidos.
- Todo se hace en una única transacción: si algo falla, no se crea el préstamo
  ni cambia el ejemplar.

## Errores

Formato: `{ "message": "...", "codigo": "...", "errors": { ... } }`.

| HTTP | codigo | Cuándo | message |
|---|---|---|---|
| 400 | DATOS_NO_VALIDOS | Falta libroId, no es positivo o el JSON no es válido | Revisa los datos del préstamo. (con el error del campo en errors.libroId) |
| 401 | — | Sin sesión | — |
| 403 | — | No es estudiante o falta el token CSRF | — |
| 404 | LIBRO_NO_ENCONTRADO | El libro no existe o está retirado | El libro no existe. |
| 409 | PRESTAMOS_VENCIDOS | Tiene préstamos vencidos sin devolver | Tienes préstamos vencidos sin devolver. Devuélvelos para poder pedir otro libro. |
| 409 | LIMITE_PRESTAMOS | Ya tiene 5 préstamos activos | Has alcanzado el límite de 5 préstamos activos. |
| 409 | SIN_EJEMPLARES | No queda ningún ejemplar libre | No quedan ejemplares disponibles de este libro. |

## Concurrencia

- Se bloquea la fila del estudiante: dos peticiones suyas a la vez se atienden
  de una en una y no pueden superar el límite de 5.
- Se bloquea la fila del libro: dos estudiantes que piden el mismo libro a la
  vez se atienden de uno en uno; el segundo ve las copias que quedan.
- Los bloqueos se toman siempre en el mismo orden (estudiante y después libro)
  para evitar esperas cruzadas.
- Red de seguridad en PostgreSQL: el índice `uq_prestamos_ejemplar_activo` (V10)
  impide dos préstamos activos del mismo ejemplar.

## Devolución autónoma (US08)

`POST /api/prestamos/{id}/devolucion` requiere sesión de estudiante y la
cabecera `X-CSRF-TOKEN`. El identificador del estudiante se obtiene de la
sesión; el cliente no lo aporta. No se necesita cuerpo. Ejemplo de respuesta:

```json
{ "id": 12, "estado": "DEVUELTO", "fechaDevolucion": "2026-11-17T12:40:00" }
```

Una devolución válida registra la fecha real, conserva el préstamo en el
historial y deja disponible el ejemplar activo. Préstamo inexistente o ajeno:
404 (`PRESTAMO_NO_ENCONTRADO`); ya devuelto: 409 (`PRESTAMO_YA_DEVUELTO`).
Sin autorización se devuelve 401; rol incorrecto o CSRF inválido, 403. El
filtro CSRF puede responder 403 a una petición anónima que carezca de token.
Los errores de negocio incluyen `message`, `codigo` y `errors`; los de
seguridad usan `message`.

El servicio usa una transacción y bloqueos pesimistas en orden usuario, libro,
ejemplar y préstamo; comprueba propiedad y estado antes de cerrar el préstamo.
Si la liberación falla, se revierte también el cierre. El libro puede estar
retirado. La disponibilidad se vuelve a reflejar en el catálogo.

US10 no está implementada: el punto donde se libera la copia está identificado
en `PrestamoService.devolver` para integrar la futura cola FIFO. La asignación
de reservas y el criterio FIFO no están cumplidos por esta implementación.
No existe aún pantalla ni endpoint de listado de «Mis préstamos».

Validación del 17/11/2026: suite Maven en H2, 140 pruebas, 0 fallos y 1
omitida (prueba exclusiva de PostgreSQL); 24 pruebas de servicio y API en
PostgreSQL 17 aislado, todas correctas. Incluyen sesión real, permisos, CSRF,
historial, catálogo, rollback, devoluciones simultáneas, devolución frente a
solicitud e índice único parcial. Son pruebas automatizadas, no una prueba de
carga ni una demostración de ausencia total de interbloqueos.

Modelo de datos en [modelo-prestamos.md](modelo-prestamos.md) y validación en
[validacion-prestamos.md](validacion-prestamos.md).
