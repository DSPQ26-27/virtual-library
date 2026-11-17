package com.biblioteca.backend.prestamo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Solicitud de préstamo: el estudiante elige el libro y el backend asigna un ejemplar libre. */
public record PrestamoRequest(
    @NotNull(message = "Indica el libro que quieres pedir.")
    @Positive(message = "El libro no es válido.")
    Long libroId
) {}
