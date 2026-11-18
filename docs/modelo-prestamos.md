# Modelo de datos de préstamos

Issue #57 (US07 #7). Completa la tabla `prestamos` que se creó en la
migración V8 (US06) para poder registrar los préstamos de los estudiantes.

## Tabla prestamos

| Columna | Migración | Significado |
|---|---|---|
| id | V8 | Identificador del préstamo |
| ejemplar_id | V8 | Ejemplar físico prestado (FK a `ejemplares`) |
| usuario_id | V8 | Estudiante que lo tiene (FK a `usuarios`) |
| fecha_prestamo | V8 | Cuándo se prestó |
| fecha_devolucion | V8 | Cuándo se devolvió; vacía mientras el préstamo está activo |
| fecha_vencimiento | V9, V11 | Fecha del préstamo + 30 días inicialmente; +30 días por renovación |
| estado | V9, V11 | `ACTIVO`, `DEVUELTO` o `CANCELADO` |
| numero_renovaciones | V11 | Contador de renovaciones, de 0 a 2; inicialmente 0 |

La V9 rellena el vencimiento y el estado de los préstamos que ya existieran
antes de hacer obligatorio el vencimiento.

## Ciclo de vida de un préstamo

Ejemplo: Ana se lleva el ejemplar EJ-001 el 17/11.

| Momento | fecha_prestamo | fecha_vencimiento | fecha_devolucion | estado |
|---|---|---|---|---|
| Se presta | 17/11 | 17/12 | (vacía) | ACTIVO |
| Se devuelve el 05/12 | 17/11 | 17/12 | 05/12 | DEVUELTO |

Los préstamos nunca se borran: los devueltos forman el historial del ejemplar
y del estudiante.

## Reglas de la base de datos

PostgreSQL rechaza cualquier fila que no cumpla estas reglas, aunque el código
Java tuviera un fallo.

| Regla | Migración | Qué impide |
|---|---|---|
| `ck_prestamos_estado` | V9, V11 | Estados distintos de `ACTIVO`, `DEVUELTO` y `CANCELADO` |
| `ck_prestamos_estado_devolucion` | V9, V11 | Un préstamo `ACTIVO` o `CANCELADO` con fecha de devolución, o uno `DEVUELTO` sin ella |
| `ck_prestamos_vencimiento` | V9 | Un vencimiento anterior o igual a la fecha del préstamo |
| `uq_prestamos_ejemplar_activo` | V10 | Dos préstamos `ACTIVO` del mismo ejemplar a la vez |
| `ck_prestamos_renovaciones` | V11 | Contadores inferiores a 0 o superiores a 2 |

`uq_prestamos_ejemplar_activo` es un índice único parcial: solo se aplica a
las filas `ACTIVO`, así que el historial puede tener muchos préstamos devueltos
del mismo ejemplar. Protege también frente a dos solicitudes simultáneas.
Está en `db/vendor/postgresql` (como la V6) porque H2, la base de datos de los
tests, no admite índices con `WHERE`.

Índices:

- `idx_prestamos_usuario_estado` (V9): contar rápido los préstamos activos de
  un estudiante (límite de 5 préstamos de la US07).
- `idx_prestamos_ejemplar_devolucion` (V8): saber si un ejemplar tiene un
  préstamo sin devolver.

## Clases Java (paquete prestamo)

- `Prestamo`: entidad. Al crearla pone la fecha del préstamo, el vencimiento a
  `DIAS_PRESTAMO` (30) días y el estado `ACTIVO`. `devolver()` guarda la fecha
  de devolución y pasa a `DEVUELTO`; si ya estaba devuelto no cambia nada.
- `EstadoPrestamo`: `ACTIVO`, `DEVUELTO` y `CANCELADO`; coincide con el CHECK de la tabla.
- `PrestamoRepository`:
  - `countByUsuarioIdAndEstado`: préstamos de un estudiante en un estado
    (con `ACTIVO` se comprobará el límite de 5 en la Issue #58).
  - `tienePrestamoActivo` y
    `findEjemplarIdsConPrestamoActivo` (US06): préstamos activos de ejemplares.
  - `findPropioParaActualizar`: consulta con bloqueo pesimista reutilizada en
    devoluciones y renovaciones.

La renovación de US09 suma 30 días al vencimiento y aumenta el contador dentro
de la misma transacción. V12 incorpora el registro mínimo de solicitudes de
espera. Detalles en [renovacion-prestamos.md](renovacion-prestamos.md).

## Pruebas

`backend/src/test/java/com/biblioteca/backend/prestamo/PrestamoPersistenceTests.java`
(9 tests, H2 con rollback):

- Un préstamo nuevo se guarda `ACTIVO`, sin devolución y con vencimiento a 30 días.
- Devolver lo pasa a `DEVUELTO` con fecha, sin borrarlo; devolver dos veces no
  cambia la fecha.
- El contador de préstamos activos y devueltos por estudiante es correcto.
- La tabla rechaza: un estado no válido, un `ACTIVO` con devolución, un
  `DEVUELTO` sin devolución, un vencimiento anterior al préstamo y un préstamo
  sin vencimiento.

Resultado: 113 tests del backend, 0 fallos.

Prueba manual en PostgreSQL (la V10 no existe en H2):

| Prueba | Resultado |
|---|---|
| `\d prestamos` muestra las columnas, los 3 CHECK y los índices nuevos | ✅ |
| Dos `INSERT` de préstamos activos del ejemplar EJ-0001 en el mismo comando | ✅ el segundo falla con `uq_prestamos_ejemplar_activo` |
| `SELECT COUNT(*) FROM prestamos` después del error | ✅ 0: no queda ningún préstamo a medias |

Uso de esta tabla al pedir un préstamo (Issues #58 y #59): ver
[contrato-prestamos.md](contrato-prestamos.md) y [validacion-prestamos.md](validacion-prestamos.md).
