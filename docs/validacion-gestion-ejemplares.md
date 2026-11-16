# Validación de la gestión de ejemplares — US06

Verificación realizada el **16/11/2026** sobre el commit `4184db7`, en la rama
`feature/prestamos-ejemplares`.

## Resultado

El recorrido **React → Spring Boot → PostgreSQL** se ha verificado correctamente
desde Chrome, con sesiones reales de estudiante, bibliotecario y administrador.
Las operaciones principales se realizaron mediante los formularios de React y
se contrastaron con las respuestas HTTP y los registros persistidos en PostgreSQL.
Esta ejecución no usó interceptores ni respuestas simuladas.

La validación no requirió modificar la aplicación ni añadir dependencias al
proyecto. Se han actualizado este informe y los enlaces de documentación.

## Entorno

- PostgreSQL **17.11**, contenedor existente `biblioteca-postgres`.
- Base temporal independiente: `biblioteca_us06_validacion_66994142`.
- Spring Boot en `127.0.0.1:8082`, con el controlador JDBC de PostgreSQL y sin
  activar el perfil H2 de pruebas.
- Flyway aplicó correctamente V1–V8, incluida V6 específica de PostgreSQL.
  Se comprobó también la instalación de `pg_trgm`.
- React servido por Vite en `127.0.0.1:5175`, con proxy temporal hacia 8082,
  sin modificar `vite.config.js`.
- Chrome automatizado mediante Playwright disponible fuera del proyecto.

Se crearon tres usuarios de prueba exclusivamente en la base temporal. Sus roles
de personal se asignaron antes del inicio de sesión. Los préstamos se prepararon
por SQL porque su alta y devolución todavía no tienen pantalla propia; el resto
del recorrido de gestión se efectuó desde React.

Al terminar se cerraron los servidores de validación y se eliminó únicamente la
base temporal. `biblioteca_db` mantuvo los mismos recuentos antes y después:
**100.005 libros, 5 ejemplares y 5 usuarios**. No se cambiaron sus registros, los
CSV ni `.env`.

## Criterios comprobados

| Criterio | Comprobación y resultado |
|---|---|
| Registro de ejemplares | Altas desde el formulario como bibliotecario y administrador: HTTP 201, identificador visible y fila persistida. Correcto. |
| Modificación | Cambio de código y estado desde React: HTTP 200; se conserva el identificador y la asociación al libro. Recargar mantiene los datos. Correcto. |
| Libro existente | Todos los ejemplares creados apuntan al libro correcto en SQL. Alta sobre un libro inexistente y edición/retirada usando otro libro: HTTP 404. La FK de PostgreSQL también rechaza una referencia inexistente. Correcto. |
| Identificadores duplicados | Los identificadores generados son distintos; una inserción de prueba con una PK repetida es rechazada por PostgreSQL. Código duplicado, incluso de un ejemplar retirado y desde otro libro: HTTP 409, sin crear filas. Correcto. |
| Estados | Se comprueban `DISPONIBLE`, `PRESTADO` y retirada mediante `activo=false`. Un estado desconocido devuelve HTTP 400. Correcto. |
| Disponibilidad con cero, uno y varios | Se verifican contadores y disponibilidad del catálogo con 0, 1, 2, 3 y 4 ejemplares, incluyendo combinaciones de disponibles, prestados y retirados. Correcto. |
| Retirados no disponibles | Después de retirar todos, el total sigue siendo 4 y los disponibles son 0. El libro no aparece en las vistas Disponibles ni En biblioteca; sigue en Todos sin disponibilidad. Correcto. |
| Permisos | Bibliotecario y administrador realizan alta, edición y retirada. El estudiante consulta sin ver gestión; POST, PUT y DELETE directos con su sesión y CSRF válido devuelven 403. Correcto. |
| Historial conservado | Comparación SQL antes y después de editar, cambiar estados y retirar: se conservan los identificadores de préstamo, ejemplar y usuario, y las fechas originales. Correcto. |
| Préstamos activos | Un préstamo real bloquea marcar disponible y retirar: HTTP 409. También funciona si la columna del ejemplar dice DISPONIBLE; React lo muestra prestado y lo excluye de disponibles. Correcto. |
| Pruebas automatizadas y funcionales | Suite backend, tests frontend, lint, build y recorrido real de navegador completados correctamente. |
| Integración y documentación | Integración de los tres componentes comprobada sobre la rama indicada. Informe añadido y enlaces actualizados; el commit y la fusión de esta documentación quedan dentro del flujo habitual del equipo. |

## Evidencia de disponibilidad

El libro de prueba `QA US06 PostgreSQL` recibió el identificador 5 en la base
temporal. Los valores siguientes se observaron en React; las vistas del catálogo
se comprobaron durante el recorrido:

| Situación | Total | Disponibles |
|---|---:|---:|
| Libro recién creado, sin ejemplares | 0 | 0 |
| Primer ejemplar disponible | 1 | 1 |
| Segundo ejemplar disponible | 2 | 2 |
| Tercer ejemplar marcado prestado | 3 | 2 |
| Préstamo activo registrado sobre el segundo, cuya columna seguía DISPONIBLE | 3 | 1 |
| Primer ejemplar cambiado a PRESTADO | 3 | 0 |
| Primer ejemplar vuelto a DISPONIBLE | 3 | 1 |
| Primer ejemplar retirado | 3 | 0 |
| Devolución registrada del segundo | 3 | 1 |
| Segundo ejemplar retirado | 3 | 0 |
| Tercer ejemplar cambiado a DISPONIBLE y después retirado | 3 | 0 |
| Alta de un cuarto ejemplar por administrador | 4 | 1 |
| Cuarto ejemplar editado y retirado por administrador | 4 | 0 |

Se comprobó que con algún ejemplar prestable el libro aparece como Disponible y
en la vista Disponibles. Con cero ejemplares no aparece en En biblioteca. Tras
retirar todos vuelve a quedar fuera de ambas vistas y sigue consultable en Todos.

## Evidencia del historial y la retirada

Tras las operaciones quedaron cuatro ejemplares de prueba en PostgreSQL, todos
asociados al libro 5 y con `activo=false`: identificadores **5, 6, 7 y 9**.
La numeración no tiene por qué ser consecutiva: las secuencias también pueden
avanzar en intentos de inserción rechazados.

Los dos préstamos permanecieron registrados:

| Préstamo | Ejemplar | Usuario de prueba | Fecha de préstamo | Fecha de devolución |
|---:|---:|---:|---|---|
| 1 | 5 | 3 | 01/11/2026 10:00 | 02/11/2026 10:00 |
| 2 | 6 | 3 | 03/11/2026 10:00 | 04/11/2026 10:00 |

El primer préstamo ya estaba devuelto al preparar el escenario; sus datos se
compararon sin cambios después de las ediciones y la retirada. El segundo comenzó
activo, provocó los rechazos 409 y se devolvió mediante SQL en la base de prueba.
Tras esa devolución, la retirada desde React conservó todos sus datos.

La cancelación de una retirada dejó el ejemplar activo. Confirmarla produjo 204
y mantuvo su fila. También se verificaron 401 sin sesión y 403 al escribir sin
CSRF, y ausencia de errores JavaScript o desbordamiento horizontal a 1280, 768
y 320 píxeles.

## Pruebas ejecutadas

| Comprobación | Resultado |
|---|---|
| Backend: `.\mvnw.cmd -B test` | 104 pruebas correctas, 0 fallos; 27 de ejemplares. Esta suite usa H2 aislada. |
| Frontend: `node --test tests/*.test.js` | 96 pruebas correctas. |
| Frontend: `npm.cmd run lint` | Correcto, sin avisos. |
| Frontend: `npm.cmd run build` | Correcto. |
| Recorrido automatizado Chrome–React–Spring–PostgreSQL | 11 grupos de comprobaciones correctos, con consultas SQL de contraste. |

El escenario existente `frontend/tests/ejemplares.browser.mjs` sigue disponible
para regresión con HTTP simulado. La ejecución PostgreSQL descrita aquí utilizó
un runner temporal independiente para evitar añadir configuración o código de
pruebas innecesario al proyecto.

## Repetir la comprobación

1. Preparar una base PostgreSQL de pruebas y arrancar Spring apuntando a ella,
   dejando que Flyway aplique las migraciones.
2. Arrancar React con su proxy dirigido a esa instancia de Spring.
3. Registrar tres cuentas y asignar los roles de estudiante, bibliotecario y
   administrador antes de iniciar sesión.
4. Crear un libro y recorrer las altas, cambios de estado y retiradas de la tabla
   de disponibilidad. Confirmar en SQL las asociaciones y filas conservadas.
5. Preparar un préstamo devuelto y uno activo sobre esos ejemplares. Comparar
   sus campos antes y después de las operaciones; registrar la devolución del
   activo antes de retirar ese ejemplar.
6. Probar los tres roles y los errores 400, 401, 403, 404 y 409 indicados.
7. Ejecutar las suites anteriores y retirar únicamente los datos de validación.
