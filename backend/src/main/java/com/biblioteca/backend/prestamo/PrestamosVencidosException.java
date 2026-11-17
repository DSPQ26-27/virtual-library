package com.biblioteca.backend.prestamo;

/** El estudiante está bloqueado por tener préstamos vencidos sin devolver (US12). */
public class PrestamosVencidosException extends PrestamoRechazadoException {
    public PrestamosVencidosException() {
        super("PRESTAMOS_VENCIDOS",
            "Tienes préstamos vencidos sin devolver. Devuélvelos para poder pedir otro libro.");
    }
}
