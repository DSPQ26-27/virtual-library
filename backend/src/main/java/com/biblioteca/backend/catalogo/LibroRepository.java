package com.biblioteca.backend.catalogo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibroRepository extends JpaRepository<Libro, Long>, JpaSpecificationExecutor<Libro> {

    /** Una categoría por grupo, conservando una grafía existente y sin cargar entidades. */
    @Query("""
        SELECT MIN(TRIM(l.categoria)) FROM Libro l
        WHERE l.categoria IS NOT NULL AND TRIM(l.categoria) <> ''
          AND l.retirado = false
        GROUP BY LOWER(TRIM(l.categoria))
        ORDER BY LOWER(TRIM(l.categoria))
        """)
    List<String> findCategorias();

    /** Ids de los libros cuyo ISBN, sin guiones ni espacios, coincide con el indicado (#51). */
    @Query("""
        SELECT l.id FROM Libro l
        WHERE UPPER(REPLACE(REPLACE(l.isbn, '-', ''), ' ', '')) = :isbn
        """)
    List<Long> findIdsByIsbnNormalizado(@Param("isbn") String isbn);

    /** Libro no retirado, bloqueando su fila hasta el final de la transacción (préstamos US07). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Libro l WHERE l.id = :id AND l.retirado = false")
    Optional<Libro> findParaPrestamo(@Param("id") Long id);

    /** Bloqueo del libro, también si está retirado, para devoluciones y renovaciones. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Libro l WHERE l.id = :id")
    Optional<Libro> findParaDevolucion(@Param("id") Long id);
}
