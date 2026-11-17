package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;

public record PrestamoListadoResponse(Long id, Long libroId, String titulo,
    LocalDateTime fechaPrestamo, LocalDateTime fechaVencimiento,
    LocalDateTime fechaDevolucion, EstadoPrestamo estado) {}
