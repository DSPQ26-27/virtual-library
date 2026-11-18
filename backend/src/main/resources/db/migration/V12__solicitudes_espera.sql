-- Registro mínimo para la consulta de US09. La gestión de la cola corresponde a US10.
CREATE TABLE solicitudes_espera (
    id BIGSERIAL PRIMARY KEY,
    libro_id BIGINT NOT NULL REFERENCES libros(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fecha_solicitud TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT ck_solicitudes_espera_estado CHECK (estado IN ('PENDIENTE', 'ASIGNADA', 'CANCELADA'))
);

CREATE INDEX idx_solicitudes_espera_libro_estado_fecha
    ON solicitudes_espera (libro_id, estado, fecha_solicitud, id);
