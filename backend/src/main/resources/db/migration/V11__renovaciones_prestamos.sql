-- US09: conserva los préstamos existentes con cero renovaciones.
ALTER TABLE prestamos ADD COLUMN numero_renovaciones INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_renovaciones
    CHECK (numero_renovaciones BETWEEN 0 AND 2);

ALTER TABLE prestamos DROP CONSTRAINT ck_prestamos_estado;
ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_estado
    CHECK (estado IN ('ACTIVO', 'DEVUELTO', 'CANCELADO'));

ALTER TABLE prestamos DROP CONSTRAINT ck_prestamos_estado_devolucion;
ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_estado_devolucion
    CHECK ((estado IN ('ACTIVO', 'CANCELADO') AND fecha_devolucion IS NULL)
        OR (estado = 'DEVUELTO' AND fecha_devolucion IS NOT NULL));
