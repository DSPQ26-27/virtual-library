package com.biblioteca.backend.espera;

public class SolicitudEsperaNoEncontradaException extends RuntimeException {
    public SolicitudEsperaNoEncontradaException() {
        super("La solicitud no existe o no te pertenece.");
    }
}
