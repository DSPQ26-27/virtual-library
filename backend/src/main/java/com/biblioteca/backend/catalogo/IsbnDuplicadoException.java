package com.biblioteca.backend.catalogo;

/** Ya existe otro libro con ese ISBN (HTTP 409). */
public class IsbnDuplicadoException extends RuntimeException {
    public IsbnDuplicadoException() {
        super("Ya existe un libro con ese ISBN.");
    }
}
