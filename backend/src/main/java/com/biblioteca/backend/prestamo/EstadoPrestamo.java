package com.biblioteca.backend.prestamo;

/** Estados posibles de un préstamo; coinciden con el CHECK de la tabla prestamos. */
public enum EstadoPrestamo {
    ACTIVO,
    DEVUELTO,
    CANCELADO
}
