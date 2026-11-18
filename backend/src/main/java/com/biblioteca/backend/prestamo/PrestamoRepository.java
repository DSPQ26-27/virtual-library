package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    @Query("""
        SELECT new com.biblioteca.backend.prestamo.PrestamoListadoResponse(
            p.id, l.id, l.titulo, p.fechaPrestamo, p.fechaVencimiento, p.fechaDevolucion, p.estado, p.numeroRenovaciones)
        FROM Prestamo p JOIN p.ejemplar e JOIN e.libro l
        WHERE p.usuario.id = :usuarioId
        """)
    List<PrestamoListadoResponse> listarPropios(@Param("usuarioId") Long usuarioId);

    interface UbicacionPrestamo {
        Long getLibroId();
        Long getEjemplarId();
    }

    @Query("SELECT p.ejemplar.libro.id AS libroId, p.ejemplar.id AS ejemplarId FROM Prestamo p WHERE p.id = :id AND p.usuario.id = :usuarioId")
    Optional<UbicacionPrestamo> localizarPropio(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /** Para devolver o renovar: adquirir antes el bloqueo de usuario y de libro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prestamo p WHERE p.id = :id AND p.usuario.id = :usuarioId")
    Optional<Prestamo> findPropioParaActualizar(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Query("SELECT COUNT(p) > 0 FROM Prestamo p WHERE p.ejemplar.id = :ejemplarId AND p.estado = com.biblioteca.backend.prestamo.EstadoPrestamo.ACTIVO")
    boolean tienePrestamoActivo(@Param("ejemplarId") Long ejemplarId);

    @Query("""
        SELECT DISTINCT p.ejemplar.id FROM Prestamo p
        WHERE p.ejemplar.id IN :ejemplarIds AND p.estado = com.biblioteca.backend.prestamo.EstadoPrestamo.ACTIVO
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
