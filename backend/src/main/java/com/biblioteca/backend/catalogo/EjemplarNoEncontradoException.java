package com.biblioteca.backend.catalogo;

public class EjemplarNoEncontradoException extends RuntimeException {
    public EjemplarNoEncontradoException() {
        super("El ejemplar no existe para este libro.");
    }
}
