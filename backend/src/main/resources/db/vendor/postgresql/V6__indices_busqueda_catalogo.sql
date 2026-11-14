-- V6 (#47): índices para la búsqueda del catálogo con ~100.000 libros.
-- Solo PostgreSQL: pg_trgm no existe en H2 (la base de datos de los tests).

-- Extensión de trigramas: permite indexar búsquedas "contiene" (LIKE '%texto%')
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Búsqueda por texto: las mismas expresiones que usa LibroSpecifications
CREATE INDEX idx_libros_titulo_trgm ON libros USING gin (lower(titulo) gin_trgm_ops);
CREATE INDEX idx_libros_autor_trgm  ON libros USING gin (lower(autor) gin_trgm_ops);
CREATE INDEX idx_libros_isbn_trgm   ON libros USING gin (isbn gin_trgm_ops);

-- Filtro por categoría sin distinguir mayúsculas
CREATE INDEX idx_libros_categoria_lower ON libros (lower(categoria));

-- Orden por defecto (título y desempate por id) con LIMIT: evita ordenar los 100.000
CREATE INDEX idx_libros_titulo_id ON libros (titulo, id);

-- Vistas BIBLIOTECA/DISPONIBLES y cálculo de "disponible": ejemplares de un libro por estado
CREATE INDEX idx_ejemplares_libro_activo_estado ON ejemplares (libro_id, activo, estado);
