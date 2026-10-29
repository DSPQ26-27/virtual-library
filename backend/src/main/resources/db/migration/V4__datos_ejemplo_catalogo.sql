-- Datos de ejemplo para probar el catálogo en desarrollo.
INSERT INTO libros (isbn, titulo, autor, editorial, categoria, anio_publicacion) VALUES
('978-84-376-0494-7', 'Don Quijote de la Mancha', 'Miguel de Cervantes', 'Cátedra', 'Clásicos', 1605),
('978-84-9759-381-9', 'Cien años de soledad', 'Gabriel García Márquez', 'Debolsillo', 'Novela', 1967),
('978-84-206-5121-6', 'La casa de Bernarda Alba', 'Federico García Lorca', 'Alianza', 'Teatro', 1945),
('978-01-3235-088-4', 'Clean Code', 'Robert C. Martin', 'Prentice Hall', 'Informática', 2008);

INSERT INTO ejemplares (libro_id, codigo, estado) VALUES
((SELECT id FROM libros WHERE titulo = 'Don Quijote de la Mancha'), 'EJ-0001', 'DISPONIBLE'),
((SELECT id FROM libros WHERE titulo = 'Don Quijote de la Mancha'), 'EJ-0002', 'PRESTADO'),
((SELECT id FROM libros WHERE titulo = 'Cien años de soledad'), 'EJ-0003', 'PRESTADO'),
((SELECT id FROM libros WHERE titulo = 'Clean Code'), 'EJ-0004', 'DISPONIBLE');
