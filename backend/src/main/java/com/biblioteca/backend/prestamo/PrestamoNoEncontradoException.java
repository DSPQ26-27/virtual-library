package com.biblioteca.backend.prestamo;

public class PrestamoNoEncontradoException extends RuntimeException {
    public PrestamoNoEncontradoException() {
        super("Préstamo no encontrado.");
    }
}
