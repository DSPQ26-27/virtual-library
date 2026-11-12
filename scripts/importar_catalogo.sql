
BEGIN;

-- Tablas temporales para cargar los CSV.
CREATE TEMP TABLE carga_libros AS
SELECT
    isbn, titulo, autor, editorial, categoria,
    anio_publicacion, descripcion, portada_url,
    openlibrary_key, openlibrary_work_key, origen
FROM libros WITH NO DATA;

CREATE TEMP TABLE carga_materias (
    nombre VARCHAR(300)
);

CREATE TEMP TABLE carga_relaciones (
    openlibrary_key VARCHAR(50),
    materia_nombre VARCHAR(300)
);

COPY carga_libros
FROM '/tmp/libros_import.csv'
WITH (FORMAT CSV, HEADER TRUE);

COPY carga_materias
FROM '/tmp/materias_import.csv'
WITH (FORMAT CSV, HEADER TRUE);

COPY carga_relaciones
FROM '/tmp/libro_materias_import.csv'
WITH (FORMAT CSV, HEADER TRUE);

-- Comprobar que los candidatos tienen identificadores únicos.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM carga_libros
        WHERE isbn IS NULL
           OR openlibrary_key IS NULL
           OR titulo IS NULL
           OR autor IS NULL
    ) THEN
        RAISE EXCEPTION 'Hay libros sin datos obligatorios';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM carga_libros
        GROUP BY isbn
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay ISBN duplicados en el CSV';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM carga_libros
        GROUP BY openlibrary_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay claves Open Library duplicadas';
    END IF;
END $$;

-- Incorporar libros nuevos sin alterar los existentes.
INSERT INTO libros (
    isbn, titulo, autor, editorial, categoria,
    anio_publicacion, descripcion, portada_url,
    openlibrary_key, openlibrary_work_key, origen
)
SELECT
    c.isbn, c.titulo, c.autor, c.editorial, c.categoria,
    c.anio_publicacion, c.descripcion, c.portada_url,
    c.openlibrary_key, c.openlibrary_work_key, c.origen
FROM carga_libros c
WHERE NOT EXISTS (
    SELECT 1
    FROM libros l
    WHERE l.openlibrary_key = c.openlibrary_key
       OR (
           l.isbn IS NOT NULL
           AND regexp_replace(l.isbn, '[^0-9]', '', 'g') = c.isbn
       )
)
ON CONFLICT DO NOTHING;

-- Incorporar materias nuevas.
INSERT INTO materias (nombre)
SELECT DISTINCT nombre
FROM carga_materias
WHERE nombre IS NOT NULL AND nombre <> ''
ON CONFLICT (nombre) DO NOTHING;

-- Relacionar materias con los libros correspondientes.
-- También reconoce ISBN existentes escritos con guiones.
INSERT INTO libro_materias (libro_id, materia_id)
SELECT DISTINCT l.id, m.id
FROM carga_relaciones r
JOIN carga_libros c
    ON c.openlibrary_key = r.openlibrary_key
JOIN materias m
    ON m.nombre = r.materia_nombre
JOIN LATERAL (
    SELECT id
    FROM libros l
    WHERE l.openlibrary_key = c.openlibrary_key
       OR (
           l.isbn IS NOT NULL
           AND regexp_replace(l.isbn, '[^0-9]', '', 'g') = c.isbn
       )
    ORDER BY
        CASE WHEN l.openlibrary_key = c.openlibrary_key
             THEN 0 ELSE 1 END,
        l.id
    LIMIT 1
) l ON TRUE
ON CONFLICT DO NOTHING;

COMMIT;

SELECT
    (SELECT COUNT(*) FROM libros) AS total_libros,
    (SELECT COUNT(*) FROM materias) AS total_materias,
    (SELECT COUNT(*) FROM libro_materias) AS total_relaciones,
    (SELECT COUNT(*) FROM ejemplares) AS total_ejemplares;
