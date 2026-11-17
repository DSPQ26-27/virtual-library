package com.biblioteca.backend.prestamo;

public class PrestamoYaDevueltoException extends RuntimeException {
    public PrestamoYaDevueltoException() {
        super("El préstamo ya ha sido devuelto.");
    }
}
