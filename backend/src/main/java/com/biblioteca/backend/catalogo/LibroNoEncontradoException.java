package com.biblioteca.backend.catalogo;

/** El libro solicitado no existe (HTTP 404). */
public class LibroNoEncontradoException extends RuntimeException {
    public LibroNoEncontradoException() {
        super("El libro no existe.");
    }
}
