-- Catálogo (#27): un libro puede tener varios ejemplares identificados individualmente.
CREATE TABLE libros (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(17) UNIQUE,
    titulo VARCHAR(255) NOT NULL,
    autor VARCHAR(255) NOT NULL,
    editorial VARCHAR(150),
    categoria VARCHAR(100),
    anio_publicacion INTEGER
);

CREATE TABLE ejemplares (
    id BIGSERIAL PRIMARY KEY,
    libro_id BIGINT NOT NULL REFERENCES libros(id),
    codigo VARCHAR(50) NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_ejemplares_estado CHECK (estado IN ('DISPONIBLE', 'PRESTADO'))
);

CREATE INDEX idx_ejemplares_libro ON ejemplares(libro_id);
