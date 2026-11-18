package com.biblioteca.backend.espera;

import java.time.LocalDateTime;

public record SolicitudEsperaResponse(
        Long id,
        Long libroId,
        String titulo,
        LocalDateTime fechaSolicitud,
        EstadoSolicitudEspera estado
) {

    public static SolicitudEsperaResponse from(SolicitudEspera solicitud) {
        return new SolicitudEsperaResponse(
                solicitud.getId(),
                solicitud.getLibro().getId(),
                solicitud.getLibro().getTitulo(),
                solicitud.getFechaSolicitud(),
                solicitud.getEstado()
        );
    }
}
