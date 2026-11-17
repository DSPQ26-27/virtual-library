package com.biblioteca.backend.prestamo;

import java.time.LocalDateTime;

public record DevolucionResponse(Long id, EstadoPrestamo estado, LocalDateTime fechaDevolucion) {}
