package com.biblioteca.backend.catalogo;

import java.util.List;

/** Una página del catálogo: los libros de la página y los datos para navegar entre páginas. */
public record PaginaLibrosResponse(
    List<LibroResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
