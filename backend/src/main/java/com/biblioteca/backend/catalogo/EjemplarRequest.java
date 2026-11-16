package com.biblioteca.backend.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos editables de un ejemplar; el libro y su identificador no cambian. */
public record EjemplarRequest(
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 50, message = "El código no puede superar 50 caracteres.")
        String codigo,

        @NotNull(message = "Selecciona un estado válido.")
        EstadoEjemplar estado) {

    public EjemplarRequest {
        codigo = codigo == null ? null : codigo.trim();
    }
}
