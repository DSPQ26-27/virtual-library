-- V7 (#51): retirada lógica de libros.
-- Retirar no borra la fila: se conservan sus ejemplares y su historial de préstamos.
ALTER TABLE libros ADD COLUMN retirado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE libros ADD COLUMN fecha_retirada TIMESTAMP;
