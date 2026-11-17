package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    boolean existsByEjemplarIdAndFechaDevolucionIsNull(Long ejemplarId);

    @Query("""
        SELECT DISTINCT p.ejemplar.id FROM Prestamo p
        WHERE p.ejemplar.id IN :ejemplarIds AND p.fechaDevolucion IS NULL
        """)
    Set<Long> findEjemplarIdsConPrestamoActivo(@Param("ejemplarIds") Collection<Long> ejemplarIds);

    /** Préstamos de un usuario en un estado; con ACTIVO se comprueba el límite de 5 (US07). */
    long countByUsuarioIdAndEstado(Long usuarioId, EstadoPrestamo estado);

    boolean existsByUsuarioIdAndEstadoAndFechaVencimientoBefore(
        Long usuarioId, EstadoPrestamo estado, LocalDateTime fecha);

    /** Bloqueo por retraso (US12): algún préstamo activo ya vencido. */
    default boolean tienePrestamosVencidos(Long usuarioId) {
        return existsByUsuarioIdAndEstadoAndFechaVencimientoBefore(
            usuarioId, EstadoPrestamo.ACTIVO, LocalDateTime.now());
    }
}
