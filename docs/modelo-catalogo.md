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
- Un ejemplar es prestable si está activo y DISPONIBLE.
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
