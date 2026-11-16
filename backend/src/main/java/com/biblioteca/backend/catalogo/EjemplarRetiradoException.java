package com.biblioteca.backend.catalogo;

public class EjemplarRetiradoException extends RuntimeException {
    public EjemplarRetiradoException() {
        super("No se puede editar un ejemplar retirado.");
    }
}
