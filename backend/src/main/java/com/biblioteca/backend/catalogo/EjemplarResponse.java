package com.biblioteca.backend.catalogo;

public record EjemplarResponse(Long id, String codigo, EstadoEjemplar estado, boolean activo) {

    static EjemplarResponse from(Ejemplar ejemplar) {
        return new EjemplarResponse(ejemplar.getId(), ejemplar.getCodigo(),
            ejemplar.getEstado(), ejemplar.isActivo());
    }
}
