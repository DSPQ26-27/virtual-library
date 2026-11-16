-- Registro mínimo para bloquear retiradas con préstamos activos y conservar su historial.
-- Sin borrado en cascada: retirar un ejemplar solo cambia su campo activo.
CREATE TABLE prestamos (
    id BIGSERIAL PRIMARY KEY,
    ejemplar_id BIGINT NOT NULL REFERENCES ejemplares(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fecha_prestamo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_devolucion TIMESTAMP
);

CREATE INDEX idx_prestamos_ejemplar_devolucion ON prestamos(ejemplar_id, fecha_devolucion);
