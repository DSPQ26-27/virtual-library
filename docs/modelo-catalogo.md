# Modelo de datos del catálogo

Implementado en la Issue #27 (US03). Base para la API de libros (#28).

## Tablas (migración V3)

**libros**: id, isbn (único, opcional), titulo, autor, editorial,
categoria, anio_publicacion.

**ejemplares**: id, libro_id (→ libros), codigo (único), estado, activo.

- Un libro puede tener varios ejemplares, cada uno identificado por su código.
- estado: DISPONIBLE o PRESTADO (restricción CHECK en la base de datos).
- activo = false marca un ejemplar dado de baja sin borrarlo, para
  conservar el historial de préstamos.
- La retirada rechaza ejemplares `PRESTADO` o con préstamos sin devolución
  (tabla `prestamos`, migración V8). Se conservan los préstamos devueltos.
- Un ejemplar es prestable si está activo y DISPONIBLE.
  La disponibilidad de la API exige además que no tenga préstamos activos.
  Un libro estará "disponible" en el catálogo si tiene al menos un ejemplar prestable.

## Clases Java (paquete catalogo)

Libro, Ejemplar, EstadoEjemplar, LibroRepository, EjemplarRepository.

## Datos de ejemplo (migración V4)

| Libro | Ejemplares | ¿Disponible? |
|---|---|---|
| Don Quijote de la Mancha | EJ-0001 disponible, EJ-0002 prestado | Sí |
| Cien años de soledad | EJ-0003 prestado | No |
| La casa de Bernarda Alba | ninguno | No |
| Clean Code | EJ-0004 disponible | Sí |

## Pruebas

CatalogoPersistenceTests: datos de muestra cargados, guardado y lectura
de libro con ejemplar, ejemplares no prestables, estado no válido
y código de ejemplar duplicado.

## Búsqueda e índices (#47)

La búsqueda del catálogo (LibroSpecifications, LibroService) filtra por
texto, categoría y vista, y pagina en PostgreSQL. La migración V6
(db/vendor/postgresql/V6__indices_busqueda_catalogo.sql) solo se aplica en
PostgreSQL, porque usa la extensión pg_trgm, que no existe en H2:

- idx_libros_titulo_trgm, idx_libros_autor_trgm, idx_libros_isbn_trgm:
  índices GIN de trigramas para las búsquedas "contiene" (LIKE '%texto%').
- idx_libros_categoria_lower: filtro de categoría sin distinguir mayúsculas.
- idx_libros_titulo_id: orden por título con desempate por id.
- idx_ejemplares_libro_activo_estado: vistas BIBLIOTECA y DISPONIBLES y
  cálculo de disponible.

Flyway busca las migraciones en db/migration (comunes) y en
db/vendor/{vendor} (específicas de cada base de datos), según
spring.flyway.locations en application.properties.

## Retirada lógica (#51)

La migración V7 (db/migration/V7__retirada_logica_libros.sql) añade a libros:

- retirado: BOOLEAN NOT NULL DEFAULT FALSE. Los libros existentes quedan en catálogo.
- fecha_retirada: TIMESTAMP, cuándo se retiró.

La entidad Libro expone dos métodos de gestión:

- actualizarDatos(...): cambia solo los datos bibliográficos editables
  (ISBN, título, autor, editorial, categoría, año, descripción y portada).
  No permite cambiar id, origen ni las claves de Open Library.
- retirar(): marca el libro como retirado y guarda la fecha. Si ya estaba
  retirado no hace nada.

Retirar no borra el libro ni sus ejemplares. La búsqueda (LibroSpecifications)
y las categorías excluyen los libros retirados. Ver contrato-gestion-catalogo.md.

## Gestión de ejemplares y préstamos (US06)

La migración V8 añade `prestamos`: id, ejemplar_id, usuario_id, fecha_prestamo y
fecha_devolucion. Una devolución nula indica un préstamo activo. Las relaciones
no tienen borrado en cascada; retirar un ejemplar conserva su historial.

El listado y las vistas del catálogo excluyen de disponibilidad los ejemplares
con préstamos activos, incluso si su estado almacenado indica DISPONIBLE.
Contrato y reglas en [gestion-ejemplares.md](gestion-ejemplares.md).
Validación completa con PostgreSQL en
[validacion-gestion-ejemplares.md](validacion-gestion-ejemplares.md).
