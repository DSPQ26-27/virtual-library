package com.biblioteca.backend.catalogo;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EjemplarRepository extends JpaRepository<Ejemplar, Long> {

    List<Ejemplar> findByLibroIdOrderByIdAsc(Long libroId);

    /** Serializa edición y retirada para que una edición simultánea no reactive el ejemplar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ejemplar> findByIdAndLibroId(Long id, Long libroId);

    Optional<Ejemplar> findByCodigo(String codigo);

    /** De los libros indicados, devuelve los ids de los que tienen algún ejemplar activo y DISPONIBLE. */
    @Query("""
        SELECT DISTINCT e.libro.id FROM Ejemplar e
        WHERE e.libro.id IN :libroIds
          AND e.activo = true
          AND e.estado = com.biblioteca.backend.catalogo.EstadoEjemplar.DISPONIBLE
        """)
    Set<Long> findLibrosConEjemplarPrestable(@Param("libroIds") Collection<Long> libroIds);
}
