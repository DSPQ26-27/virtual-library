package com.biblioteca.backend.catalogo;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EjemplarRepository extends JpaRepository<Ejemplar, Long> {

    /** De los libros indicados, devuelve los ids de los que tienen algún ejemplar activo y DISPONIBLE. */
    @Query("""
        SELECT DISTINCT e.libro.id FROM Ejemplar e
        WHERE e.libro.id IN :libroIds
          AND e.activo = true
          AND e.estado = com.biblioteca.backend.catalogo.EstadoEjemplar.DISPONIBLE
        """)
    Set<Long> findLibrosConEjemplarPrestable(@Param("libroIds") Collection<Long> libroIds);
}
