package com.biblioteca.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LibroRepository extends JpaRepository<Libro, Long>, JpaSpecificationExecutor<Libro> {

    /** Una categoría por grupo, conservando una grafía existente y sin cargar entidades. */
    @Query("""
        SELECT MIN(TRIM(l.categoria)) FROM Libro l
        WHERE l.categoria IS NOT NULL AND TRIM(l.categoria) <> ''
        GROUP BY LOWER(TRIM(l.categoria))
        ORDER BY LOWER(TRIM(l.categoria))
        """)
    List<String> findCategorias();
}
