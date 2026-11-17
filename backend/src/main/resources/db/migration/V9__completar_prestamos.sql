-- Completa el registro de préstamos creado en la V8 (Issue #57, US07).

-- 1. Columnas nuevas
ALTER TABLE prestamos ADD COLUMN fecha_vencimiento TIMESTAMP;
ALTER TABLE prestamos ADD COLUMN estado VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL;

-- 2. Rellenar los préstamos que ya existieran
UPDATE prestamos SET fecha_vencimiento = fecha_prestamo + INTERVAL '30' DAY;
UPDATE prestamos SET estado = 'DEVUELTO' WHERE fecha_devolucion IS NOT NULL;

-- 3. Ahora que todas las filas lo tienen, el vencimiento es obligatorio
ALTER TABLE prestamos ALTER COLUMN fecha_vencimiento SET NOT NULL;

-- 4. Reglas que la base de datos hace cumplir
ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_estado
    CHECK (estado IN ('ACTIVO', 'DEVUELTO'));

ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_estado_devolucion
    CHECK ((estado = 'ACTIVO' AND fecha_devolucion IS NULL)
        OR (estado = 'DEVUELTO' AND fecha_devolucion IS NOT NULL));

ALTER TABLE prestamos ADD CONSTRAINT ck_prestamos_vencimiento
    CHECK (fecha_vencimiento > fecha_prestamo);

-- 5. Índice para contar rápido los préstamos activos de un estudiante (límite de 5)
CREATE INDEX idx_prestamos_usuario_estado ON prestamos(usuario_id, estado);
