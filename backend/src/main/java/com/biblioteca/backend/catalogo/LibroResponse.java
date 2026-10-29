
package com.biblioteca.backend.catalogo;

public record LibroResponse(
    Long id,
    String titulo,
    String autor,
    boolean disponible
) {}
