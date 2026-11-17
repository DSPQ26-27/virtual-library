package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;

/** Datos del préstamo creado, para mostrar la confirmación y el vencimiento. */
public record PrestamoResponse(
    Long id,
    Long libroId,
    String titulo,
    String ejemplarCodigo,
    LocalDateTime fechaPrestamo,
    LocalDateTime fechaVencimiento,
    EstadoPrestamo estado
) {
    public static PrestamoResponse from(Prestamo prestamo) {
        var ejemplar = prestamo.getEjemplar();
        var libro = ejemplar.getLibro();
        return new PrestamoResponse(prestamo.getId(), libro.getId(), libro.getTitulo(),
            ejemplar.getCodigo(), prestamo.getFechaPrestamo(), prestamo.getFechaVencimiento(),
            prestamo.getEstado());
    }
}
