-- Un ejemplar no puede tener dos préstamos activos a la vez (Issue #57, US07).
-- Solo PostgreSQL: H2 no admite índices únicos parciales (con WHERE).
CREATE UNIQUE INDEX uq_prestamos_ejemplar_activo
    ON prestamos (ejemplar_id)
    WHERE estado = 'ACTIVO';
