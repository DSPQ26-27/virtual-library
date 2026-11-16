package com.biblioteca.backend.catalogo;

/** Ficha completa de un libro para la gestión del catálogo (#51). */
public record LibroDetalleResponse(
        Long id,
        String isbn,
        String titulo,
        String autor,
        String editorial,
        String categoria,
        Integer anioPublicacion,
        String descripcion,
        String portadaUrl,
        String origen,
        boolean retirado) {

    static LibroDetalleResponse from(Libro libro) {
        return new LibroDetalleResponse(libro.getId(), libro.getIsbn(), libro.getTitulo(),
            libro.getAutor(), libro.getEditorial(), libro.getCategoria(),
            libro.getAnioPublicacion(), libro.getDescripcion(), libro.getPortadaUrl(),
            libro.getOrigen(), libro.isRetirado());
    }
}
