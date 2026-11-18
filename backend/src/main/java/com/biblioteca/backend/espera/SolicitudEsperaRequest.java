package com.biblioteca.backend.espera;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SolicitudEsperaRequest(
        @NotNull(message = "Indica el libro para la lista de espera.")
        @Positive(message = "El identificador del libro debe ser positivo.")
        Long libroId
) {}
