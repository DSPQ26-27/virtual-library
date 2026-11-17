package com.biblioteca.backend.catalogo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EjemplarDevolucionTests {
    @Test
    void unaCopiaPrestadaYActivaRecuperaLaDisponibilidad() {
        Ejemplar ejemplar = new Ejemplar(null, "PRUEBA");
        ejemplar.prestar();

        ejemplar.liberarTrasDevolucion();

        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
        assertTrue(ejemplar.isPrestable());
    }

    @Test
    void noSeLiberaUnaCopiaDisponibleORetirada() {
        Ejemplar disponible = new Ejemplar(null, "DISPONIBLE");
        assertThrows(IllegalStateException.class, disponible::liberarTrasDevolucion);

        Ejemplar retirada = new Ejemplar(null, "RETIRADA");
        retirada.prestar();
        retirada.retirar();
        assertThrows(IllegalStateException.class, retirada::liberarTrasDevolucion);
        assertEquals(EstadoEjemplar.PRESTADO, retirada.getEstado());
        assertFalse(retirada.isActivo());
    }
}
