package com.biblioteca.backend.prestamo;

public class RenovacionRechazadaException extends PrestamoRechazadoException {
    public RenovacionRechazadaException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
