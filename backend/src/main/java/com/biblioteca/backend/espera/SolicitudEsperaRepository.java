package com.biblioteca.backend.espera;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolicitudEsperaRepository extends JpaRepository<SolicitudEspera, Long> {
    boolean existsByLibroIdAndEstado(Long libroId, EstadoSolicitudEspera estado);

    boolean existsByLibroIdAndUsuarioIdAndEstado(
            Long libroId,
            Long usuarioId,
            EstadoSolicitudEspera estado);

    List<SolicitudEspera> findByUsuarioIdOrderByFechaSolicitudAscIdAsc(Long usuarioId);

    List<SolicitudEspera> findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(
            Long libroId, EstadoSolicitudEspera estado);
}
