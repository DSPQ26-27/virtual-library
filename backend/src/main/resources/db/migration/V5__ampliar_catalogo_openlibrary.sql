
-- V5: Preparar catálogo para Open Library

-- 1. Ampliar los datos bibliográficos
ALTER TABLE libros
ADD COLUMN descripcion TEXT;

ALTER TABLE libros
ADD COLUMN portada_url VARCHAR(1000);

-- Identificador único de la edición (/books/OL...M)
ALTER TABLE libros
ADD COLUMN openlibrary_key VARCHAR(50) UNIQUE;

-- Identificador de la obra (/works/OL...W)
-- No es único: una obra puede tener varias ediciones
ALTER TABLE libros
ADD COLUMN openlibrary_work_key VARCHAR(50);

-- Distinguir registros manuales e importados
ALTER TABLE libros
ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE libros
ADD CONSTRAINT ck_libros_origen
CHECK (origen IN ('MANUAL', 'OPEN_LIBRARY'));

-- 2. Tabla de materias
CREATE TABLE materias (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(300) NOT NULL UNIQUE
);

-- 3. Relación muchos a muchos
CREATE TABLE libro_materias (
    libro_id BIGINT NOT NULL REFERENCES libros(id),
    materia_id BIGINT NOT NULL REFERENCES materias(id),
    PRIMARY KEY (libro_id, materia_id)
);

-- 4. Índices iniciales
CREATE INDEX idx_libros_openlibrary_work
ON libros(openlibrary_work_key);

CREATE INDEX idx_libros_categoria
ON libros(categoria);

CREATE INDEX idx_libro_materias_materia
ON libro_materias(materia_id);
