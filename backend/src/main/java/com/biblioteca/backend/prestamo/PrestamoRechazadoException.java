package com.biblioteca.backend.prestamo;

/** Un préstamo que no cumple las reglas de la US07 (HTTP 409). */
public abstract class PrestamoRechazadoException extends RuntimeException {

    private final String codigo;

    protected PrestamoRechazadoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
