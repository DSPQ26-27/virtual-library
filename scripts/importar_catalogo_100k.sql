\set ON_ERROR_STOP on
BEGIN;

-- El CSV se carga desde dentro del contenedor, no desde Windows.
CREATE TEMP TABLE carga_libros AS
SELECT isbn, titulo, autor, editorial, categoria, anio_publicacion,
       descripcion, portada_url, openlibrary_key, openlibrary_work_key, origen
FROM libros WITH NO DATA;

CREATE TEMP TABLE carga_materias (nombre VARCHAR(300));
CREATE TEMP TABLE carga_relaciones (openlibrary_key VARCHAR(50), materia_nombre VARCHAR(300));

COPY carga_libros FROM '/tmp/libros_import.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');
COPY carga_materias FROM '/tmp/materias_import.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');
COPY carga_relaciones FROM '/tmp/libro_materias_import.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM carga_libros WHERE isbn IS NULL OR openlibrary_key IS NULL OR titulo IS NULL OR autor IS NULL OR origen <> 'OPEN_LIBRARY') THEN
        RAISE EXCEPTION 'CSV con libros incompletos u origen incorrecto';
    END IF;
    IF EXISTS (SELECT 1 FROM carga_libros GROUP BY isbn HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'ISBN repetido en CSV';
    END IF;
    IF EXISTS (SELECT 1 FROM carga_libros GROUP BY openlibrary_key HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Clave Open Library repetida en CSV';
    END IF;
    IF (SELECT COUNT(*) FROM carga_libros) <> 100000 THEN
        RAISE EXCEPTION 'Se esperaban 100000 libros, se obtuvieron %', (SELECT COUNT(*) FROM carga_libros);
    END IF;
    IF EXISTS (
        SELECT 1 FROM carga_relaciones r
        LEFT JOIN carga_libros c ON c.openlibrary_key = r.openlibrary_key
        WHERE c.openlibrary_key IS NULL
    ) THEN
        RAISE EXCEPTION 'Hay materias asociadas a libros ausentes';
    END IF;
END $$;

-- Crear un índice temporal de ISBN normalizados para evitar hacer
-- regexp_replace en cada comprobación de cada candidato.
CREATE TEMP TABLE isbn_existentes AS
SELECT DISTINCT ON (isbn_normalizado) isbn_normalizado, id
FROM (
    SELECT regexp_replace(isbn, '[^0-9]', '', 'g') AS isbn_normalizado, id
    FROM libros WHERE isbn IS NOT NULL
) x
WHERE isbn_normalizado <> ''
ORDER BY isbn_normalizado, id;
CREATE UNIQUE INDEX ON isbn_existentes (isbn_normalizado);

-- Los libros preexistentes (y sus ejemplares) no se modifican.
INSERT INTO libros (
    isbn, titulo, autor, editorial, categoria, anio_publicacion,
    descripcion, portada_url, openlibrary_key, openlibrary_work_key, origen
)
SELECT c.isbn, c.titulo, c.autor, c.editorial, c.categoria,
       c.anio_publicacion, c.descripcion, c.portada_url,
       c.openlibrary_key, c.openlibrary_work_key, c.origen
FROM carga_libros c
LEFT JOIN libros por_clave ON por_clave.openlibrary_key = c.openlibrary_key
LEFT JOIN isbn_existentes por_isbn ON por_isbn.isbn_normalizado = c.isbn
WHERE por_clave.id IS NULL AND por_isbn.id IS NULL
ON CONFLICT DO NOTHING;

-- Reconstruir el índice de ISBN tras insertar los libros nuevos.
TRUNCATE isbn_existentes;
INSERT INTO isbn_existentes (isbn_normalizado, id)
SELECT DISTINCT ON (isbn_normalizado) isbn_normalizado, id
FROM (
    SELECT regexp_replace(isbn, '[^0-9]', '', 'g') AS isbn_normalizado, id
    FROM libros WHERE isbn IS NOT NULL
) x
WHERE isbn_normalizado <> ''
ORDER BY isbn_normalizado, id;

CREATE TEMP TABLE mapa_libros AS
SELECT c.openlibrary_key,
       COALESCE(por_clave.id, por_isbn.id) AS libro_id
FROM carga_libros c
LEFT JOIN libros por_clave ON por_clave.openlibrary_key = c.openlibrary_key
LEFT JOIN isbn_existentes por_isbn ON por_isbn.isbn_normalizado = c.isbn;
CREATE UNIQUE INDEX ON mapa_libros (openlibrary_key);

INSERT INTO materias (nombre)
SELECT DISTINCT nombre FROM carga_materias
WHERE nombre IS NOT NULL AND nombre <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO libro_materias (libro_id, materia_id)
SELECT DISTINCT ml.libro_id, m.id
FROM carga_relaciones r
JOIN mapa_libros ml ON ml.openlibrary_key = r.openlibrary_key
JOIN materias m ON m.nombre = r.materia_nombre
WHERE ml.libro_id IS NOT NULL
ON CONFLICT DO NOTHING;

COMMIT;

SELECT origen, COUNT(*) AS total FROM libros GROUP BY origen ORDER BY origen;
SELECT COUNT(*) AS total_materias FROM materias;
SELECT COUNT(*) AS total_relaciones FROM libro_materias;
SELECT COUNT(*) AS ejemplares_sin_modificar FROM ejemplares;
