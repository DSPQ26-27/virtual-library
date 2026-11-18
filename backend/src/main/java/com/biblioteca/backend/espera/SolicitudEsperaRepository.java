package com.biblioteca.backend.espera;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface SolicitudEsperaRepository extends JpaRepository<SolicitudEspera, Long> {
    /** Primer bloqueo de cada operación. Se libera al finalizar la transacción. */
    @Query(value = "SELECT id FROM coordinacion_prestamos WHERE id = 1 FOR UPDATE", nativeQuery = true)
    List<Integer> bloquearOperaciones();

    boolean existsByLibroIdAndEstado(Long libroId, EstadoSolicitudEspera estado);

    boolean existsByLibroIdAndUsuarioIdAndEstado(
            Long libroId,
            Long usuarioId,
            EstadoSolicitudEspera estado);

    List<SolicitudEspera> findByUsuarioIdOrderByFechaSolicitudAscIdAsc(Long usuarioId);

    List<SolicitudEspera> findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(
            Long libroId, EstadoSolicitudEspera estado);
}
