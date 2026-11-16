package com.biblioteca.backend.catalogo;

public class CodigoEjemplarDuplicadoException extends RuntimeException {
    public CodigoEjemplarDuplicadoException() {
        super("Ya existe un ejemplar con ese código.");
    }
}
