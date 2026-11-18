package com.biblioteca.backend.espera;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudEsperaRepository extends JpaRepository<SolicitudEspera, Long> {
    boolean existsByLibroIdAndEstado(Long libroId, EstadoSolicitudEspera estado);
}
