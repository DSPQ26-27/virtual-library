package com.biblioteca.backend.espera;

public class SolicitudEsperaRechazadaException extends RuntimeException {

    private final String codigo;

    public SolicitudEsperaRechazadaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
