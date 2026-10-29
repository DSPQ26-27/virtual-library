-- El contrato de registro admite correos de hasta 254 caracteres (límite del estándar).
ALTER TABLE usuarios ALTER COLUMN email SET DATA TYPE VARCHAR(254);
