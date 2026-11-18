package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;

public record RenovacionResponse(Long id, EstadoPrestamo estado,
    int numeroRenovaciones, LocalDateTime fechaVencimiento) {}
