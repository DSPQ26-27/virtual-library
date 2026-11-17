package com.biblioteca.backend.prestamo;

/** El estudiante ya tiene el máximo de préstamos activos. */
public class LimitePrestamosException extends PrestamoRechazadoException {
    public LimitePrestamosException() {
        super("LIMITE_PRESTAMOS", "Has alcanzado el límite de 5 préstamos activos.");
    }
}
