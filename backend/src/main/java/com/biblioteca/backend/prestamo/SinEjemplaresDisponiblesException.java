package com.biblioteca.backend.prestamo;

/** No queda ningún ejemplar del libro que se pueda prestar. */
public class SinEjemplaresDisponiblesException extends PrestamoRechazadoException {
    public SinEjemplaresDisponiblesException() {
        super("SIN_EJEMPLARES", "No quedan ejemplares disponibles de este libro.");
    }
}
